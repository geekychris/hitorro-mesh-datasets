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
    void registry_loads_bundled_and_can_find_by_identifier() {
        DatasetRegistry reg = new DatasetRegistry().loadBundled();
        assertThat(reg.all()).hasSizeGreaterThanOrEqualTo(3);

        // All three manifests speak iso3166alpha2 — country-info produces it,
        // cities maps to it via the ISO country code, Natural Earth produces it.
        // Lookup should surface every dataset that touches the namespace.
        assertThat(reg.byIdentifier("iso3166alpha2"))
                .extracting(Manifest::id)
                .contains("geonames-country-info", "natural-earth-countries");

        // Both country-shaped datasets can be reached from a "geonames" query
        // (cities produces it; country-info maps to it).
        assertThat(reg.byIdentifier("geonames"))
                .extracting(Manifest::id)
                .contains("geonames-cities15000", "geonames-country-info");
    }
}
