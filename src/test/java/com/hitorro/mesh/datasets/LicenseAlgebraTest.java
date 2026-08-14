/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.loader.LicenseAlgebra;
import com.hitorro.mesh.datasets.model.License;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseAlgebraTest {

    @Test
    void two_permissive_licenses_stay_permissive() {
        LicenseAlgebra.Result r = LicenseAlgebra.combine(List.of(
                License.cc0("Wikidata"),
                License.ccBy("GeoNames")));
        assertThat(r.redistribution()).isTrue();
        assertThat(r.commercialUse()).isTrue();
        assertThat(r.attributionRequired()).isTrue();     // CC-BY carries it
        assertThat(r.shareAlike()).isFalse();
        assertThat(r.attributions()).contains("GeoNames (CC-BY-4.0)");
    }

    @Test
    void mixing_osm_forces_share_alike() {
        LicenseAlgebra.Result r = LicenseAlgebra.combine(List.of(
                License.ccBy("GeoNames"),
                License.odbl("OpenStreetMap")));
        assertThat(r.shareAlike()).isTrue();
        assertThat(r.warnings()).anySatisfy(w ->
                assertThat(w).contains("ODbL-1.0").contains("share-alike"));
    }

    @Test
    void public_domain_is_fully_permissive_alone() {
        LicenseAlgebra.Result r = LicenseAlgebra.combine(List.of(
                License.publicDomain("Natural Earth")));
        assertThat(r.fullyPermissive()).isTrue();
    }
}
