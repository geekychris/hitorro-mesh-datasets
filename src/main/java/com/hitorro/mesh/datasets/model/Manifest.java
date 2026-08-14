/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The publishable dataset descriptor.
 *
 * <p>A {@code Manifest} is everything a mesh needs to install, load, register,
 * and semantically join a dataset:</p>
 * <ol>
 *   <li>identity: {@link #id()}, {@link #version()}</li>
 *   <li>legal: {@link #license()}</li>
 *   <li>fetch: {@link #source()}</li>
 *   <li>shape: {@link #record()}</li>
 *   <li>what identifiers it speaks: {@link #identifiers()}</li>
 *   <li>what capabilities/roles it advertises: {@link #provides()}</li>
 *   <li>how to partition it across agents: {@link #partitionBy()}</li>
 *   <li>how to join it to other datasets: {@link #relationships()}</li>
 * </ol>
 *
 * <p>Manifests live on disk as YAML under {@code src/main/resources/manifests/}
 * and are shipped inside the jar so any deployment has the full catalog
 * available even before install scripts run.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Manifest(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description,
        @JsonProperty("license") License license,
        @JsonProperty("source") Source source,
        @JsonProperty("record") RecordSpec record,
        @JsonProperty("provides") List<String> provides,
        @JsonProperty("identifiers") IdentitySpec identifiers,
        @JsonProperty("partitionBy") String partitionBy,
        @JsonProperty("relationships") List<Relationship> relationships,
        /**
         * Human-facing metadata block — use cases, methodology, stats,
         * further-reading. Optional and additive: the Datasets tab renders
         * whatever's present and hides the section entirely when null.
         */
        @JsonProperty("metadata") DatasetMetadata metadata
) { }
