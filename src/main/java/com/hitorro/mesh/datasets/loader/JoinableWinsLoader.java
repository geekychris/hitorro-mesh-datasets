/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.loader;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.hitorro.mesh.datasets.model.JoinableWin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Reads the bundled {@code /joinable-wins.yaml} — a curated list of
 * cross-dataset joins that return real rows today. Cheap; loaded once
 * at driver startup by whatever bean wants to expose the list.
 */
public final class JoinableWinsLoader {

    private static final ObjectMapper YAML = new YAMLMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JoinableWinsLoader() { }

    /** Load the bundled joinable-wins.yaml. Returns empty list if missing. */
    @SuppressWarnings("unchecked")
    public static List<JoinableWin> loadBundled() {
        try (InputStream in = JoinableWinsLoader.class.getResourceAsStream("/joinable-wins.yaml")) {
            if (in == null) return List.of();
            Map<String, Object> root = YAML.readValue(in, Map.class);
            Object wins = root.get("wins");
            if (!(wins instanceof List<?>)) return List.of();
            // Round-trip via Jackson to convert Map entries → JoinableWin records.
            return YAML.convertValue(wins,
                    YAML.getTypeFactory().constructCollectionType(List.class, JoinableWin.class));
        } catch (IOException e) {
            System.err.println("[datasets] failed to load joinable-wins.yaml: " + e.getMessage());
            return List.of();
        }
    }
}
