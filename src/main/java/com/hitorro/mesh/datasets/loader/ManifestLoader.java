/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.hitorro.mesh.datasets.model.Manifest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses YAML manifest files into {@link Manifest} records.
 *
 * <p>Two lookup paths:</p>
 * <ol>
 *   <li>{@link #loadBundled(String)} — from the datasets jar's
 *       {@code /manifests/} classpath resource. Every deployment carries the
 *       full catalog even without install scripts having run.</li>
 *   <li>{@link #loadFrom(Path)} — from a filesystem path (for local
 *       modifications during development).</li>
 * </ol>
 */
public final class ManifestLoader {

    private static final ObjectMapper YAML = new YAMLMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ManifestLoader() { }

    /** Load a manifest bundled inside the datasets jar. */
    public static Manifest loadBundled(String id) throws IOException {
        String path = "/manifests/" + id + ".yaml";
        try (InputStream in = ManifestLoader.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("no bundled manifest: " + path);
            return YAML.readValue(in, Manifest.class);
        }
    }

    /** Load a manifest from any filesystem path (yaml or json). */
    public static Manifest loadFrom(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        ObjectMapper m = (name.endsWith(".json")) ? JSON : YAML;
        return m.readValue(Files.readAllBytes(file), Manifest.class);
    }

    /**
     * Enumerate every bundled manifest by scanning the resource directory.
     * Cheap; called by {@code DatasetRegistry} at startup to warm the catalog.
     */
    public static List<String> listBundled() {
        // Resource enumeration inside a jar is awkward without extra deps.
        // The catalog is small and hand-maintained — see docs/CATALOG.md
        // for the canonical list. Update this array when adding a manifest.
        List<String> out = new ArrayList<>();
        for (String id : BUNDLED) {
            if (ManifestLoader.class.getResource("/manifests/" + id + ".yaml") != null) {
                out.add(id);
            }
        }
        return out;
    }

    private static final String[] BUNDLED = {
            "geonames-cities15000",
            "geonames-country-info",
            "natural-earth-countries",
            "wikidata-cities",
            "wikidata-countries",
            "noaa-ghcnd-stations",
            "worldbank-indicators",
            "usgs-earthquakes",
            "owid-co2-latest",
            "wikipedia-pageviews",
            "osm-airports"
    };
}
