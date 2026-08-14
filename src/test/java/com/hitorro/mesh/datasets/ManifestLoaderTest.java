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
    void registry_loads_bundled_and_can_find_by_identifier() {
        DatasetRegistry reg = new DatasetRegistry().loadBundled();
        assertThat(reg.all()).hasSizeGreaterThanOrEqualTo(2);

        // Both manifests speak the "geonames" identifier namespace (one produces
        // it, the other maps to it) — the registry lookup should return both.
        assertThat(reg.byIdentifier("geonames"))
                .extracting(Manifest::id)
                .contains("geonames-cities15000", "geonames-country-info");
    }
}
