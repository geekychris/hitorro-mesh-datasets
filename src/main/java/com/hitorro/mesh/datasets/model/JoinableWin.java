/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A curated cross-dataset join that returns real rows today.
 *
 * <p>Every entry in the shipped {@code joinable-wins.yaml} deserialises
 * into one of these. The driver's {@code GET /mesh/datasets/wins}
 * endpoint returns the list verbatim; the UI renders it as clickable
 * cards that copy the SQL into the Playground editor.</p>
 *
 * <p>Not part of the {@link Manifest} hierarchy — a "win" is
 * cross-manifest by definition. Kept as its own record so future
 * additions (row-count sample, latency measurement, health check) fit
 * cleanly.</p>
 *
 * @param title     one-sentence "what this join tells you"
 * @param datasets  manifest ids involved, in JOIN order
 * @param kind      {@code exact-id | spatial | hub | mixed}
 * @param hops      number of JOIN edges (1 for direct, 2 for one-hub-hop)
 * @param license   combined-licence hint: {@code permissive |
 *                  attribution-required | share-alike | non-commercial}
 * @param semantic  true iff the SQL should be run with
 *                  {@code semantic=true} on {@code /mesh/queries}
 * @param sql       the exact query — dispatchable as-is against a mesh
 *                  that has every listed dataset installed
 * @param note      one-line explanation of what makes the join
 *                  interesting or the caveats a reader should know
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinableWin(
        @JsonProperty("title") String title,
        @JsonProperty("datasets") List<String> datasets,
        @JsonProperty("kind") String kind,
        @JsonProperty("hops") Integer hops,
        @JsonProperty("license") String license,
        @JsonProperty("semantic") Boolean semantic,
        @JsonProperty("sql") String sql,
        @JsonProperty("note") String note
) { }
