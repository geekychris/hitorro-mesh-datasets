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

        // Three declared relationships, one per join target — the reason
        // Wikidata unlocks queries the other datasets couldn't do alone.
        assertThat(m.relationships()).hasSize(3);
        assertThat(m.relationships())
                .extracting(r -> r.target())
                .containsExactlyInAnyOrder(
                        "geonames-cities15000",
                        "geonames-country-info",
                        "natural-earth-countries");
    }

    @Test
    void registry_loads_bundled_and_can_find_by_identifier() {
        DatasetRegistry reg = new DatasetRegistry().loadBundled();
        assertThat(reg.all()).hasSizeGreaterThanOrEqualTo(4);

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
