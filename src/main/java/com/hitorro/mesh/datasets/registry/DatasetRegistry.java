/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.registry;

import com.hitorro.mesh.datasets.loader.ManifestLoader;
import com.hitorro.mesh.datasets.model.Manifest;
import com.hitorro.mesh.datasets.model.Relationship;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory catalog of every dataset manifest known to this process.
 *
 * <p>Populated at startup from the bundled resources in {@code /manifests/}
 * and optionally augmented at runtime via {@link #register(Manifest)} for
 * datasets installed off-tree.</p>
 *
 * <p>The registry is the piece the query planner will eventually consult to
 * resolve {@code USING PLACE} / {@code USING ENTITY} — it walks the graph
 * of manifests looking for a path of identifier equivalences from source
 * to target. For now it just holds and searches the metadata.</p>
 */
public final class DatasetRegistry {

    private final Map<String, Manifest> byId = new LinkedHashMap<>();

    /** Load every bundled manifest. Errors on individual files are logged, not thrown. */
    public synchronized DatasetRegistry loadBundled() {
        for (String id : ManifestLoader.listBundled()) {
            try {
                byId.put(id, ManifestLoader.loadBundled(id));
            } catch (IOException e) {
                System.err.println("[datasets] failed to load bundled manifest " + id + ": " + e.getMessage());
            }
        }
        return this;
    }

    public synchronized void register(Manifest m) {
        byId.put(m.id(), m);
    }

    public synchronized Manifest get(String id) {
        return byId.get(id);
    }

    /**
     * Look up a manifest by the SQL table name it produces.
     *
     * <p>Manifest ids use kebab-case for readability
     * ({@code geonames-cities15000}); SQL tables use underscores
     * ({@code geonames_cities15000}). This is the reverse of
     * {@code MeshRegistrar.tableName(Manifest)} and lets the semantic-join
     * rewriter go from a table name in SQL back to the manifest that
     * declares its relationships.</p>
     *
     * @return the manifest, or {@code null} if no bundled/installed manifest
     *         produces a table of that name.
     */
    public synchronized Manifest byTableName(String tableName) {
        String id = tableName.replace('_', '-');
        Manifest exact = byId.get(id);
        if (exact != null) return exact;
        // Fallback: some ids contain non-hyphenated digits (e.g. cities15000).
        // Try every manifest and compare its computed table name.
        for (Manifest m : byId.values()) {
            if (m.id().replace('-', '_').equals(tableName)) return m;
        }
        return null;
    }

    public synchronized Collection<Manifest> all() {
        return new ArrayList<>(byId.values());
    }

    /**
     * Find every manifest whose identifier {@code produces} or {@code maps}
     * list contains the given namespace. This is how the planner locates
     * candidate join targets given only a namespace name.
     */
    public synchronized List<Manifest> byIdentifier(String namespace) {
        List<Manifest> out = new ArrayList<>();
        for (Manifest m : byId.values()) {
            if (m.identifiers() == null) continue;
            List<String> prod = m.identifiers().produces();
            List<String> maps = m.identifiers().maps();
            if ((prod != null && prod.contains(namespace))
                    || (maps != null && maps.contains(namespace))) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Every direct relationship this dataset declares. Useful for graph
     * traversal (BFS across relationships when resolving a semantic join).
     */
    public synchronized List<Relationship> outboundFrom(String datasetId) {
        Manifest m = byId.get(datasetId);
        if (m == null || m.relationships() == null) return List.of();
        return new ArrayList<>(m.relationships());
    }

    /**
     * Scan {@code $HITORRO_DATASETS_HOME} (default {@code ~/.hitorro/datasets})
     * for any directory containing a {@code manifest.yaml} and load them.
     * Positions this class as the "what's actually installed on this box"
     * source for a future auto-registration hook — a Spring Boot module can
     * scan on start-up and call {@code MeshRegistrar} for every hit.
     *
     * <p>Manifests loaded here override bundled ones with the same id — an
     * install-time manifest is more current than the resource baked into
     * the jar.</p>
     *
     * @return list of dataset ids that were loaded from disk
     */
    public synchronized List<String> scanInstalled() {
        Path home = installedHome();
        List<String> loaded = new ArrayList<>();
        if (!Files.isDirectory(home)) return loaded;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(home)) {
            for (Path child : children) {
                Path mf = child.resolve("manifest.yaml");
                if (!Files.isRegularFile(mf)) continue;
                try {
                    Manifest m = ManifestLoader.loadFrom(mf);
                    byId.put(m.id(), m);
                    loaded.add(m.id());
                } catch (IOException e) {
                    System.err.println("[datasets] skipping " + mf + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[datasets] scanInstalled failed at " + home + ": " + e.getMessage());
        }
        return loaded;
    }

    /**
     * The install root — resolved in this order:
     * <ol>
     *   <li>{@code hitorro.datasets.home} system property (tests, launcher flags)</li>
     *   <li>{@code HITORRO_DATASETS_HOME} env var (matches {@code common.sh})</li>
     *   <li>{@code ~/.hitorro/datasets}</li>
     * </ol>
     */
    public static Path installedHome() {
        String prop = System.getProperty("hitorro.datasets.home");
        if (prop != null && !prop.isBlank()) return Paths.get(prop);
        String env = System.getenv("HITORRO_DATASETS_HOME");
        if (env != null && !env.isBlank()) return Paths.get(env);
        return Paths.get(System.getProperty("user.home"), ".hitorro", "datasets");
    }
}
