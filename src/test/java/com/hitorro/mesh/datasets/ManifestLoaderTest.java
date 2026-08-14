/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.loader.ManifestLoader;
import com.hitorro.mesh.datasets.model.Manifest;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestLoaderTest {

    @Test
    void bundled_manifests_carry_metadata_blocks() throws Exception {
        // Every shipped manifest should have a `metadata` block with at
        // least useCases + stats. Non-shipped can omit for MVP; catches
        // "someone added a new dataset and forgot the docs" regressions.
        for (String id : new String[] {
                "geonames-cities15000", "geonames-country-info",
                "natural-earth-countries", "wikidata-cities",
                "wikidata-countries", "noaa-ghcnd-stations",
                "worldbank-indicators", "usgs-earthquakes",
                "owid-co2-latest", "wikipedia-pageviews",
                "osm-airports", "wikidata-city-sitelinks",
                "openalex-institutions", "coingecko-crypto",
                "github-top-repos" }) {
            Manifest m = ManifestLoader.loadBundled(id);
            assertThat(m.metadata())
                    .withFailMessage("%s: metadata block missing", id)
                    .isNotNull();
            assertThat(m.metadata().useCases())
                    .withFailMessage("%s: useCases missing / empty", id)
                    .isNotNull().isNotEmpty();
            assertThat(m.metadata().stats())
                    .withFailMessage("%s: stats block missing", id)
                    .isNotNull();
            assertThat(m.metadata().stats().rowCount())
                    .withFailMessage("%s: rowCount missing", id)
                    .isNotNull().isPositive();
        }
    }

    @Test
    void bundled_geonames_cities_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("geonames-cities15000");
        assertThat(m.id()).isEqualTo("geonames-cities15000");
        assertThat(m.license().spdx()).isEqualTo("CC-BY-4.0");
        assertThat(m.license().shareAlike()).isFalse();
        assertThat(m.record().primaryKey()).isEqualTo("geonameid");
        assertThat(m.record().fields()).extracting("name")
                .contains("geonameid", "name", "latitude", "longitude", "country_code", "population");
        assertThat(m.partitionBy()).isEqualTo("country_code");
        assertThat(m.identifiers().produces()).contains("geonames");
        assertThat(m.relationships()).isNotEmpty();
    }

    @Test
    void bundled_country_info_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("geonames-country-info");
        assertThat(m.id()).isEqualTo("geonames-country-info");
        assertThat(m.partitionBy()).isNull();  // broadcast, not partitioned
        assertThat(m.identifiers().produces())
                .contains("iso3166alpha2", "iso3166alpha3", "iso3166numeric", "fips");
    }

    @Test
    void bundled_natural_earth_countries_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("natural-earth-countries");
        assertThat(m.id()).isEqualTo("natural-earth-countries");
        assertThat(m.license().spdx()).isEqualTo("Public-Domain");
        assertThat(m.license().attributionRequired()).isFalse();
        assertThat(m.record().primaryKey()).isEqualTo("iso_a3");
        assertThat(m.partitionBy()).isNull();
        assertThat(m.identifiers().produces())
                .contains("iso3166alpha2", "iso3166alpha3", "iso3166numeric", "unm49", "worldbank_a2");
        // Should declare both an EXACT_ID join to country-info and a SPATIAL
        // join to the cities table — proves multi-kind relationships work.
        assertThat(m.relationships()).hasSize(2);
        assertThat(m.relationships())
                .extracting(r -> r.kind().name())
                .containsExactlyInAnyOrder("EXACT_ID", "SPATIAL");
    }

    @Test
    void bundled_wikidata_cities_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("wikidata-cities");
        assertThat(m.id()).isEqualTo("wikidata-cities");
        assertThat(m.license().spdx()).isEqualTo("CC0-1.0");
        assertThat(m.license().attributionRequired()).isFalse();  // CC0
        assertThat(m.record().primaryKey()).isEqualTo("wikidata_qid");
        assertThat(m.partitionBy()).isNull();  // broadcast

        // The whole point of this dataset — it produces wikidata AND maps to
        // both geonames and iso3166alpha2. That's what makes it identity glue.
        assertThat(m.identifiers().produces()).containsExactly("wikidata");
        assertThat(m.identifiers().maps())
                .contains("geonames", "iso3166alpha2");

        // Four declared relationships — the country_qid FK back to
        // wikidata-countries was added when the hub dataset landed.
        assertThat(m.relationships()).hasSize(4);
        assertThat(m.relationships())
                .extracting(r -> r.target())
                .containsExactlyInAnyOrder(
                        "geonames-cities15000",
                        "geonames-country-info",
                        "natural-earth-countries",
                        "wikidata-countries");
    }

    @Test
    void bundled_noaa_ghcnd_stations_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("noaa-ghcnd-stations");
        assertThat(m.id()).isEqualTo("noaa-ghcnd-stations");
        assertThat(m.license().spdx()).isEqualTo("Public-Domain");
        assertThat(m.record().primaryKey()).isEqualTo("station_id");
        assertThat(m.partitionBy()).isNull();  // broadcast

        // Derived fips_country carries id.fips role — critical for the
        // rewriter to match against geonames-country-info.fips.
        assertThat(m.record().fields())
                .filteredOn(f -> "fips_country".equals(f.name()))
                .singleElement()
                .extracting("role")
                .isEqualTo("id.fips");

        // Two relationships, two different kinds — the first dataset to
        // ship both EXACT_ID and SPATIAL relationships out of the box.
        assertThat(m.relationships()).hasSize(2);
        assertThat(m.relationships())
                .extracting(r -> r.kind().name())
                .containsExactlyInAnyOrder("EXACT_ID", "SPATIAL");
    }

    @Test
    void bundled_wikidata_countries_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("wikidata-countries");
        assertThat(m.id()).isEqualTo("wikidata-countries");
        assertThat(m.license().spdx()).isEqualTo("CC0-1.0");
        assertThat(m.record().primaryKey()).isEqualTo("wikidata_qid");
        assertThat(m.partitionBy()).isNull();

        // Full identifier menagerie — maps to every other country namespace
        // any dataset in the catalog speaks.
        assertThat(m.identifiers().maps())
                .contains("iso3166alpha2", "iso3166alpha3", "iso3166numeric", "fips", "unm49");
        // Three relationships out to the other country-shaped datasets.
        // The wikidata-cities edge is declared on the CITY manifest instead
        // (via: country_qid) — a country doesn't join to a city via its
        // own QID, and the earlier reverse declaration made the rewriter
        // emit nonsense wd.wikidata_qid = wc.wikidata_qid.
        assertThat(m.relationships()).hasSize(3);
    }

    @Test
    void bundled_worldbank_indicators_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("worldbank-indicators");
        assertThat(m.id()).isEqualTo("worldbank-indicators");
        assertThat(m.license().spdx()).isEqualTo("CC-BY-4.0");
        assertThat(m.license().attributionRequired()).isTrue();
        assertThat(m.record().primaryKey()).isEqualTo("iso_a3");
        // Three EXACT_ID relationships — every country-scoped dataset can
        // pull World Bank stats without an intermediate hop.
        assertThat(m.relationships()).hasSize(3);
    }

    @Test
    void bundled_usgs_earthquakes_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("usgs-earthquakes");
        assertThat(m.id()).isEqualTo("usgs-earthquakes");
        assertThat(m.license().spdx()).isEqualTo("Public-Domain");
        assertThat(m.record().primaryKey()).isEqualTo("event_id");
        // First dataset whose only declared relationship is SPATIAL —
        // proves the manifest system carries the metadata even before
        // the rewriter learns how to resolve it.
        assertThat(m.relationships()).hasSize(1);
        assertThat(m.relationships().get(0).kind().name()).isEqualTo("SPATIAL");
    }

    @Test
    void bundled_owid_co2_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("owid-co2-latest");
        assertThat(m.id()).isEqualTo("owid-co2-latest");
        assertThat(m.license().spdx()).isEqualTo("CC-BY-4.0");
        assertThat(m.record().primaryKey()).isEqualTo("iso_a3");
        // Four EXACT_ID relationships — one hop from any country dataset.
        assertThat(m.relationships()).hasSize(4);
    }

    @Test
    void bundled_wikipedia_pageviews_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("wikipedia-pageviews");
        assertThat(m.id()).isEqualTo("wikipedia-pageviews");
        assertThat(m.license().spdx()).isEqualTo("CC-BY-SA-3.0");
        assertThat(m.license().shareAlike()).isTrue();
        // Deliberately no relationships — this is a fresh identifier
        // namespace that will get name-based probabilistic joins later.
        assertThat(m.relationships()).isEmpty();
    }

    @Test
    void natural_earth_manifest_declares_bbox_role_fields() throws Exception {
        // Bounding-box columns are what the SPATIAL rewriter needs to
        // resolve. Lock them into the manifest so removing them without
        // a migration path breaks a test, not a query.
        Manifest m = ManifestLoader.loadBundled("natural-earth-countries");
        assertThat(m.record().fields())
                .extracting("role")
                .contains("geo.bbox.min_lat", "geo.bbox.max_lat",
                          "geo.bbox.min_lon", "geo.bbox.max_lon");
    }

    @Test
    void bundled_osm_airports_manifest_parses_with_share_alike_license() throws Exception {
        Manifest m = ManifestLoader.loadBundled("osm-airports");
        assertThat(m.id()).isEqualTo("osm-airports");
        assertThat(m.license().spdx()).isEqualTo("ODbL-1.0");
        // First share-alike licence in the shipped catalog. Locking this
        // in so any downstream change breaks a test not a query — every
        // join that touches osm-airports needs to warn about share-alike
        // as a database inheritance.
        assertThat(m.license().shareAlike()).isTrue();
        assertThat(m.license().attributionRequired()).isTrue();

        assertThat(m.record().primaryKey()).isEqualTo("iata_code");
        // Both EXACT_ID (country_code hop) and SPATIAL (point-in-polygon)
        // relationships to Natural Earth.
        assertThat(m.relationships()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(m.relationships())
                .extracting(r -> r.kind().name())
                .contains("EXACT_ID", "SPATIAL");
    }

    @Test
    void bundled_wikidata_city_sitelinks_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("wikidata-city-sitelinks");
        assertThat(m.id()).isEqualTo("wikidata-city-sitelinks");
        assertThat(m.license().spdx()).isEqualTo("CC0-1.0");
        assertThat(m.record().primaryKey()).isEqualTo("wikidata_qid");
        // The point of this dataset: EXACT_ID both to wikidata-cities and
        // wikipedia-pageviews so it bridges the two.
        assertThat(m.relationships()).hasSize(2);
        assertThat(m.relationships())
                .extracting(r -> r.target())
                .containsExactlyInAnyOrder("wikidata-cities", "wikipedia-pageviews");
        // enwiki_article field has the role that unlocks the join to
        // wikipedia-pageviews. Lock it in.
        assertThat(m.record().fields())
                .filteredOn(f -> "enwiki_article".equals(f.name()))
                .singleElement()
                .extracting("role")
                .isEqualTo("id.wikipedia_article");
    }

    @Test
    void bundled_openalex_institutions_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("openalex-institutions");
        assertThat(m.id()).isEqualTo("openalex-institutions");
        assertThat(m.license().spdx()).isEqualTo("CC0-1.0");
        assertThat(m.record().primaryKey()).isEqualTo("openalex_id");
        // Three EXACT_ID relationships back to country-shaped datasets.
        // The direct worldbank-indicators edge was dropped when we found
        // ISO-2 vs ISO-3 mismatch made it return zero rows — routing
        // through wikidata-countries as an explicit hub is the correct
        // pattern documented in the manifest.
        assertThat(m.relationships()).hasSize(3);
        // Metric-role fields for citations + works count so quick-query
        // buttons can propose "top by cited_by_count" idioms.
        assertThat(m.record().fields())
                .filteredOn(f -> "cited_by_count".equals(f.name()))
                .singleElement()
                .extracting("role")
                .isEqualTo("metric.citations");
    }

    @Test
    void bundled_coingecko_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("coingecko-crypto");
        assertThat(m.id()).isEqualTo("coingecko-crypto");
        // Custom LicenseRef — CoinGecko's free-tier terms don't have a
        // standard SPDX identifier.
        assertThat(m.license().spdx()).isEqualTo("LicenseRef-CoinGecko-Free");
        assertThat(m.license().attributionRequired()).isTrue();
        assertThat(m.record().primaryKey()).isEqualTo("coin_id");
        // No cross-dataset relationships — cryptos don't share an id with
        // any of the geographic / socioeconomic datasets.
        assertThat(m.relationships()).isEmpty();
    }

    @Test
    void bundled_github_top_repos_manifest_parses() throws Exception {
        Manifest m = ManifestLoader.loadBundled("github-top-repos");
        assertThat(m.id()).isEqualTo("github-top-repos");
        assertThat(m.license().spdx()).isEqualTo("CC-BY-4.0");
        assertThat(m.record().primaryKey()).isEqualTo("github_id");
        // No cross-dataset relationships — GitHub repos don't share an
        // identifier with any other catalog dataset.
        assertThat(m.relationships()).isEmpty();
        // metric.stars is what quick-query buttons hook into for "top by …"
        assertThat(m.record().fields())
                .filteredOn(f -> "stargazers_count".equals(f.name()))
                .singleElement()
                .extracting("role")
                .isEqualTo("metric.stars");
    }

    @Test
    void registry_loads_bundled_and_can_find_by_identifier() {
        DatasetRegistry reg = new DatasetRegistry().loadBundled();
        assertThat(reg.all()).hasSizeGreaterThanOrEqualTo(15);

        // All country-shaped manifests speak iso3166alpha2 — country-info
        // produces it, Natural Earth produces it, Wikidata cities maps to it.
        assertThat(reg.byIdentifier("iso3166alpha2"))
                .extracting(Manifest::id)
                .contains("geonames-country-info", "natural-earth-countries", "wikidata-cities");

        // Every dataset that speaks geonames — cities produces it, country-info
        // maps to it, Wikidata cities maps to it. Now three datasets converge
        // on the GeoNames namespace, which is what makes cross-source joins
        // through GeoNames IDs first-class in the planner.
        assertThat(reg.byIdentifier("geonames"))
                .extracting(Manifest::id)
                .contains("geonames-cities15000", "geonames-country-info", "wikidata-cities");

        // And wikidata-cities is the sole producer of the wikidata namespace —
        // making it the single point every future join through Wikidata QIDs
        // will land on.
        assertThat(reg.byIdentifier("wikidata"))
                .extracting(Manifest::id)
                .contains("wikidata-cities");
    }
}
