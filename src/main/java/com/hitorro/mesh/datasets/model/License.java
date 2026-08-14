/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * License algebra for a dataset.
 *
 * <p>Rather than a bare SPDX string, capture the four capabilities that
 * actually matter when the planner needs to answer: <i>"can the result of
 * this join be redistributed, and under what terms?"</i></p>
 *
 * <ul>
 *   <li>{@code commercialUse} — may downstream results be sold?</li>
 *   <li>{@code redistribution} — may we hand the data to a third party?</li>
 *   <li>{@code attributionRequired} — must consumers preserve credit?</li>
 *   <li>{@code shareAlike} — does a derivative inherit this same licence?
 *       (e.g. ODbL's "share-alike as a database"; CC-BY-SA on content.)</li>
 *   <li>{@code modification} — may we transform the source records?</li>
 * </ul>
 *
 * <p>See {@code docs/LICENSE_ALGEBRA.md} for the compatibility matrix the
 * driver uses when a query touches datasets under different terms.</p>
 */
public record License(
        @JsonProperty("spdx") String spdx,
        @JsonProperty("attribution") String attribution,
        @JsonProperty("commercialUse") boolean commercialUse,
        @JsonProperty("redistribution") boolean redistribution,
        @JsonProperty("attributionRequired") boolean attributionRequired,
        @JsonProperty("shareAlike") boolean shareAlike,
        @JsonProperty("modification") boolean modification
) {

    /** CC0 — Wikidata, OpenAlex, some Census products. */
    public static License cc0(String attribution) {
        return new License("CC0-1.0", attribution, true, true, false, false, true);
    }

    /** CC-BY-4.0 — GeoNames, World Bank, Our World in Data, most Crossref. */
    public static License ccBy(String attribution) {
        return new License("CC-BY-4.0", attribution, true, true, true, false, true);
    }

    /** Public domain — Natural Earth, US Census (federal government works). */
    public static License publicDomain(String attribution) {
        return new License("Public-Domain", attribution, true, true, false, false, true);
    }

    /** ODbL-1.0 — OpenStreetMap. Share-alike as a database. */
    public static License odbl(String attribution) {
        return new License("ODbL-1.0", attribution, true, true, true, true, true);
    }
}
