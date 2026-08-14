/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One column in a dataset record.
 *
 * <p>The {@code role} carries the semantic type that the query planner
 * uses to reason about cross-dataset joins. For example, a field with
 * {@code role: "id.geonames"} on one dataset and {@code role: "id.geonames"}
 * on another means the planner can join them by that field even if the
 * columns have different local names.</p>
 *
 * <p>Recognised roles today:</p>
 * <ul>
 *   <li>{@code id} — this dataset's primary identifier</li>
 *   <li>{@code id.<namespace>} — foreign identifier
 *       (e.g. {@code id.wikidata}, {@code id.geonames})</li>
 *   <li>{@code name} / {@code name.<lang>} — canonical or per-language name</li>
 *   <li>{@code geo.lat} / {@code geo.lon} / {@code geo.geometry}</li>
 *   <li>{@code geo.parent.<level>} — parent area code (country, admin1, ...)</li>
 *   <li>{@code geo.population} / {@code geo.elevation}</li>
 *   <li>{@code time.<event>} — timestamped events (observation, publication, ...)</li>
 *   <li>{@code metric.<name>} — measured/aggregated value</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldSpec(
        @JsonProperty("name") String name,
        @JsonProperty("type") String type,
        @JsonProperty("role") String role,
        @JsonProperty("description") String description
) {

    public FieldSpec(String name, String type) {
        this(name, type, null, null);
    }

    public FieldSpec(String name, String type, String role) {
        this(name, type, role, null);
    }
}
