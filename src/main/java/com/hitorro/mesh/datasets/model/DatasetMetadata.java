/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Rich human-facing metadata for a dataset. Optional block on a
 * {@link Manifest} — the driver's {@code /mesh/datasets/{id}} endpoint
 * returns whatever's present verbatim so the UI can show a "what is
 * this? how was it collected? how much of it is there?" block.
 *
 * <p>Everything on this record is descriptive, not machine-load-bearing:
 * the rewriter and registrar never read these fields. They exist so the
 * Datasets tab stops being a bare schema browser and starts being a real
 * catalog page.</p>
 *
 * @param useCases       short bullets — one line per common query pattern
 * @param methodology    how the upstream source produces + maintains the data
 * @param stats          row count, size, cadence, coverage
 * @param furtherReading a URL to authoritative docs (README, license page,
 *                       API reference) — the "learn more" link
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetMetadata(
        @JsonProperty("useCases") List<String> useCases,
        @JsonProperty("methodology") String methodology,
        @JsonProperty("stats") DatasetStats stats,
        @JsonProperty("furtherReading") String furtherReading
) {

    /**
     * Compact statistics block. All fields optional so tiny manifests
     * can omit anything they don't have a good number for.
     *
     * @param rowCount        approximate rows shipped (varies with refresh)
     * @param sizeBytes       approximate size on disk after install
     * @param refreshCadence  short label: "static", "daily", "monthly",
     *                        "on-demand", "quarterly", ...
     * @param coverage        one-line summary of scope: "Every UN-recognized
     *                        country", "US-only, 2020-present", ...
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DatasetStats(
            @JsonProperty("rowCount") Long rowCount,
            @JsonProperty("sizeBytes") Long sizeBytes,
            @JsonProperty("refreshCadence") String refreshCadence,
            @JsonProperty("coverage") String coverage
    ) { }
}
