/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A declared relationship from this dataset to another target dataset.
 *
 * <p>Examples in manifest form:</p>
 * <pre>
 * # geonames city → geonames country
 * relationships:
 *   - target: geonames-country-info
 *     kind: EXACT_ID
 *     via: countryCode
 *
 * # geonames city → wikidata entity (probabilistic)
 * relationships:
 *   - target: wikidata
 *     kind: PROBABILISTIC
 *     via: [name, countryCode, latitude, longitude]
 *     params: { method: "name+geo+parent", minConfidence: 0.9 }
 *
 * # noaa station → country boundary (spatial containment)
 * relationships:
 *   - target: natural-earth-countries
 *     kind: SPATIAL
 *     via: [latitude, longitude]
 *     params: { predicate: "within", targetField: "geometry" }
 * </pre>
 *
 * @param target dataset id of the join target, or an identifier namespace
 *               ({@code "wikidata"}) for late-bound resolution
 * @param kind   see {@link RelationshipKind}
 * @param via    the local field(s) that carry the join key
 * @param params kind-specific parameters (predicate, method, ...)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Relationship(
        @JsonProperty("target") String target,
        @JsonProperty("kind") RelationshipKind kind,
        @JsonProperty("via") List<String> via,
        @JsonProperty("params") Map<String, Object> params
) { }
