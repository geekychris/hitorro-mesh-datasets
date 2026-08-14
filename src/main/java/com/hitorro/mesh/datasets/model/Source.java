/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How to fetch the raw dataset.
 *
 * <p>The dataset system doesn't care whether that means an HTTP zip, an S3
 * prefix, an HDFS directory, or a local BaseFile mirror — the install script
 * is responsible for materialising it. The manifest just records where the
 * canonical copy lives so redistributions can be verified against the same
 * URL/checksum.</p>
 *
 * @param type      transport: {@code "http" | "s3" | "hdfs" | "local" | "git"}
 * @param url       canonical URL of the source archive
 * @param format    logical format: {@code "tsv.zip"}, {@code "ndjson.gz"},
 *                  {@code "shapefile.zip"}, {@code "parquet"}, ...
 * @param checksum  {@code "sha256:..."} of the archive at download time —
 *                  install scripts refuse to proceed on mismatch. May be
 *                  {@code null} for sources that change daily.
 */
public record Source(
        @JsonProperty("type") String type,
        @JsonProperty("url") String url,
        @JsonProperty("format") String format,
        @JsonProperty("checksum") String checksum
) { }
