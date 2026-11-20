/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.loader;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
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

    // Jackson 2 defaults (as in 3.x), plus unknown properties ignored.
    private static final ObjectMapper YAML = YAMLMapper.builder().configureForJackson2()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

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
        } catch (IOException | JacksonException e) {
            System.err.println("[datasets] failed to load joinable-wins.yaml: " + e.getMessage());
            return List.of();
        }
    }
}
