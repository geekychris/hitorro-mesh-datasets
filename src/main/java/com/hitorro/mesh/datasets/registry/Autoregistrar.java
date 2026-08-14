/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.registry;

import com.hitorro.mesh.datasets.model.Manifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Wires {@link DatasetRegistry#scanInstalled()} to {@link MeshRegistrar}
 * so every dataset that's been installed on disk becomes a live table on
 * a running driver without any per-dataset scripting.
 *
 * <p>The registration rule reads the manifest's {@code partitionBy}:</p>
 * <ul>
 *   <li>{@code null} → broadcast (small dimension every agent pre-loads)</li>
 *   <li>anything else → distributed with a single "all" partition
 *       (upgrade paths for per-country / per-hash split live in future
 *       manifest evolution; the "all" default is enough for the current
 *       shipped datasets)</li>
 * </ul>
 *
 * <p>Idempotent: the driver's register endpoints happily replace an existing
 * table with the same name, and {@link #skipIds} lets callers exclude the
 * demo / sample tables they don't want touched. Every call returns a
 * {@link Report} summarising what happened so hosts (Spring
 * {@code ApplicationRunner}, CLI, ad-hoc test) can log it cleanly.</p>
 */
public final class Autoregistrar {

    private final DatasetRegistry registry;
    private final MeshRegistrar mesh;
    private final Set<String> skipIds;

    public Autoregistrar(DatasetRegistry registry, MeshRegistrar mesh, Set<String> skipIds) {
        this.registry = registry;
        this.mesh = mesh;
        this.skipIds = (skipIds == null) ? Set.of() : new LinkedHashSet<>(skipIds);
    }

    public Autoregistrar(DatasetRegistry registry, MeshRegistrar mesh) {
        this(registry, mesh, Set.of());
    }

    /**
     * Scan for installed datasets and register each one with the driver.
     * Individual failures don't abort the run — they're captured in the
     * {@link Report} so callers can decide whether to raise or continue.
     */
    public Report registerAllInstalled() {
        List<String> installed = registry.scanInstalled();
        List<String> registered = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (String id : installed) {
            if (skipIds.contains(id)) {
                skipped.add(id);
                continue;
            }
            Manifest m = registry.get(id);
            if (m == null) {
                failed.add(id + " (manifest not in registry after scan)");
                continue;
            }
            try {
                if (m.partitionBy() == null) {
                    mesh.registerBroadcast(m);
                } else {
                    mesh.registerDistributed(m, typeJsonPathFor(id), List.of("all"));
                }
                registered.add(id);
            } catch (IOException | InterruptedException e) {
                failed.add(id + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        }
        return new Report(registered, skipped, failed);
    }

    /**
     * Resolve the on-disk JVS type file for an installed dataset.
     * Matches the layout the install scripts write:
     * {@code $HITORRO_DATASETS_HOME/<id>/types/<table_name>.json}.
     */
    private static Path typeJsonPathFor(String id) {
        String tableName = id.replace('-', '_');
        Path p = DatasetRegistry.installedHome()
                .resolve(id).resolve("types").resolve(tableName + ".json");
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("no type json at " + p
                    + " — did the install script complete successfully?");
        }
        return p;
    }

    /** Summary of one registration pass. */
    public record Report(List<String> registered, List<String> skipped, List<String> failed) {

        public boolean allOk() { return failed.isEmpty(); }

        public String summary() {
            return "registered=" + registered
                    + " skipped=" + skipped
                    + " failed=" + failed;
        }
    }
}
