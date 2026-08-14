/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * The common wrapper every normalised record wears.
 *
 * <p>Preserves the native source record verbatim under {@link #nativeRecord()}
 * so no downstream re-download is needed when the semantic schema evolves —
 * the enricher just replays the mapping and rewrites the top-level fields.</p>
 *
 * <p>Modelled after the JSON envelope in the design note:</p>
 * <pre>
 * {
 *   "@id": "place:us/ca/san-francisco",
 *   "@type": ["geo.Place", "geo.City"],
 *   "names": { "en": "San Francisco", "zh": "旧金山" },
 *   "geometry": { ... },
 *   "links": { "wikidata": "Q62", "geonames": "5391959" },
 *   "source": { "dataset": "geonames", "version": "...", "recordId": "5391959" }
 * }
 * </pre>
 *
 * <p>Serialises with {@code @}-prefixed fields to keep JSON-LD hospitality
 * without pulling in an actual JSON-LD processor.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordEnvelope(
        @JsonProperty("@id") String atId,
        @JsonProperty("@type") List<String> atType,
        @JsonProperty("names") Map<String, String> names,
        @JsonProperty("geometry") JsonNode geometry,
        @JsonProperty("links") Map<String, String> links,
        @JsonProperty("source") SourceRef source,
        @JsonProperty("native") JsonNode nativeRecord
) {

    /** Provenance stamp: which manifest+version emitted this record. */
    public record SourceRef(
            @JsonProperty("dataset") String dataset,
            @JsonProperty("version") String version,
            @JsonProperty("recordId") String recordId
    ) { }
}
