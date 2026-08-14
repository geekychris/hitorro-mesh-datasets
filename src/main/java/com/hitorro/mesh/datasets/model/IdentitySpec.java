/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Identifier namespaces this dataset participates in.
 *
 * <p>Split into what the dataset <em>produces</em> (namespaces where it is
 * canonical) versus what it <em>maps</em> to (cross-references it carries,
 * so joins can hop through it). Wikidata for example produces {@code wikidata}
 * and maps to nearly every other namespace, which is what makes it the
 * identity glue for the catalog.</p>
 *
 * @param produces namespaces where this dataset is authoritative
 * @param maps     namespaces the dataset carries cross-references to
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentitySpec(
        @JsonProperty("produces") List<String> produces,
        @JsonProperty("maps") List<String> maps
) { }
