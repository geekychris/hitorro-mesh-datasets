/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.semantic.PlaceJoinRewriter;
import com.hitorro.mesh.datasets.semantic.SemanticJoinException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the rewriter against the bundled manifests — proves the same
 * relationships that show up in the driver's registry actually resolve.
 */
class PlaceJoinRewriterTest {

    private PlaceJoinRewriter rewriter;

    @BeforeEach
    void up() {
        DatasetRegistry registry = new DatasetRegistry().loadBundled();
        rewriter = new PlaceJoinRewriter(registry);
    }

    @Test
    void no_using_place_returns_unchanged() {
        String sql = "SELECT * FROM geonames_cities15000 WHERE population > 500000";
        assertThat(rewriter.rewrite(sql)).isEqualTo(sql);
    }

    @Test
    void geonames_cities_to_country_info_forward_declaration() {
        // cities → country_info; the forward relationship on the cities manifest
        // has via=[country_code], target's primary key is iso.
        String out = rewriter.rewrite(
                "SELECT * FROM geonames_cities15000 gn "
              + "JOIN geonames_country_info ci USING PLACE");
        assertThat(out).contains("JOIN geonames_country_info ci ON gn.country_code = ci.iso");
    }

    @Test
    void country_info_to_natural_earth_countries() {
        // Natural Earth declares the forward relationship (via=[iso_a2]) targeting
        // country-info. When SQL puts country-info first, we hit the REVERSE
        // path in the rewriter: target declares to source.
        String out = rewriter.rewrite(
                "SELECT * FROM geonames_country_info ci "
              + "JOIN natural_earth_countries ne USING PLACE");
        // country-info.iso = ne.iso_a2 (target-declared, via on the target side)
        assertThat(out).contains("ON ci.iso = ne.iso_a2");
    }

    @Test
    void wikidata_to_geonames_cities_inserts_cast_for_type_mismatch() {
        // wikidata_cities.geonames_id is core_string;
        // geonames_cities15000.geonameid is core_long.
        // The rewriter must wrap the source column in CAST(... AS BIGINT).
        String out = rewriter.rewrite(
                "SELECT * FROM wikidata_cities wd "
              + "JOIN geonames_cities15000 gn USING PLACE");
        assertThat(out).contains("ON CAST(wd.geonames_id AS BIGINT) = gn.geonameid");
    }

    @Test
    void three_way_chain_each_join_can_reference_any_prior() {
        // First: gn → ci forward via cities' own relationships.
        // Second: gn → ne has NO EXACT_ID declared in either direction
        //   (natural-earth's join to cities is SPATIAL, not EXACT_ID).
        //   The rewriter falls through the priors list and finds ci → ne
        //   via natural-earth's declared join back to country-info.
        //   Both resolutions are semantically correct — the rewriter picks
        //   the first prior that has a valid EXACT_ID path.
        String out = rewriter.rewrite(
                "SELECT gn.name, ci.continent, ne.income_grp "
              + "FROM geonames_cities15000 gn "
              + "JOIN geonames_country_info ci USING PLACE "
              + "JOIN natural_earth_countries ne USING PLACE "
              + "WHERE gn.population > 500000");
        assertThat(out)
                .contains("ON gn.country_code = ci.iso")
                .contains("ON ci.iso = ne.iso_a2");
        // Trailing WHERE clause preserved verbatim.
        assertThat(out).endsWith("WHERE gn.population > 500000");
    }

    @Test
    void alias_optional_falls_back_to_table_name() {
        String out = rewriter.rewrite(
                "SELECT * FROM geonames_cities15000 "
              + "JOIN geonames_country_info USING PLACE");
        assertThat(out).contains(
                "JOIN geonames_country_info ON geonames_cities15000.country_code = geonames_country_info.iso");
    }

    @Test
    void unknown_table_throws_actionable_error() {
        assertThatThrownBy(() -> rewriter.rewrite(
                "SELECT * FROM geonames_cities15000 c "
              + "JOIN nowhere_dataset x USING PLACE"))
                .isInstanceOf(SemanticJoinException.class)
                .hasMessageContaining("nowhere_dataset")
                .hasMessageContaining("install");
    }

    @Test
    void wikidata_to_natural_earth_matches_by_role_not_primary_key() {
        // wikidata_cities.country_iso has role id.iso3166alpha2 (2-letter).
        // natural_earth_countries has TWO ISO fields: iso_a2 with the same
        // role, and iso_a3 as primaryKey. Naive "join to target.primaryKey"
        // would emit ON country_iso = iso_a3 — WRONG (2-char vs 3-char, no
        // rows would ever match). The rewriter must match by matching role
        // so it emits iso_a2.
        String out = rewriter.rewrite(
                "SELECT * FROM wikidata_cities wd "
              + "JOIN natural_earth_countries ne USING PLACE");
        assertThat(out).contains("ON wd.country_iso = ne.iso_a2");
        assertThat(out).doesNotContain("iso_a3");
    }

    @Test
    void noaa_station_to_country_info_via_derived_fips_role() {
        // noaa_ghcnd_stations.fips_country has role id.fips.
        // geonames_country_info.fips also has role id.fips.
        // Different column names on both sides — role-match should still
        // wire them: s.fips_country = ci.fips.
        String out = rewriter.rewrite(
                "SELECT s.name, ci.country FROM noaa_ghcnd_stations s "
              + "JOIN geonames_country_info ci USING PLACE");
        assertThat(out).contains("ON s.fips_country = ci.fips");
    }

    @Test
    void reverse_declaration_from_wikidata_to_country_info() {
        // wikidata_cities.country_iso → geonames_country_info.iso
        String out = rewriter.rewrite(
                "SELECT * FROM geonames_country_info ci "
              + "JOIN wikidata_cities wd USING PLACE");
        // wd declares the forward join, so via=country_iso on wd, iso on ci
        // (target-declared: target=ci from wd's perspective).
        // From the SQL's perspective, ci is source and wd is target.
        // Resolver walks priors=[ci] looking for either:
        //   - ci.relationships has target=wikidata-cities → no
        //   - wd.relationships has target=geonames-country-info → YES (reverse case)
        // Which produces: ci.<ci.pk> = wd.country_iso → ci.iso = wd.country_iso
        assertThat(out).contains("ON ci.iso = wd.country_iso");
    }
}
