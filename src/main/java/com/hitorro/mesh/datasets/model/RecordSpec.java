/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shape of one record in the dataset.
 *
 * @param type       high-level type (e.g. {@code "geo.Place"}, {@code "biblio.Work"})
 * @param primaryKey name of the field that uniquely identifies a record
 * @param fields     column definitions — order matters for TSV/CSV loaders
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordSpec(
        @JsonProperty("type") String type,
        @JsonProperty("primaryKey") String primaryKey,
        @JsonProperty("fields") List<FieldSpec> fields
) { }
