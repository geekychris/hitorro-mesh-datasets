/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.registry.Autoregistrar;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.spring.DatasetsAutoRegistrationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the auto-configuration is discovered the way a Boot 4 host finds it:
 * through {@code META-INF/spring/...AutoConfiguration.imports}, not by naming
 * the class. Boot 4 no longer reads {@code spring.factories} for
 * auto-configuration, so a missing or misspelt imports entry would leave
 * host apps without the datasets beans.
 */
class DatasetsAutoConfigurationImportsTest {

    @Test
    void runsOnSpringBoot4() {
        assertThat(SpringBootVersion.getVersion()).startsWith("4.");
    }

    @Test
    void importsFileListsTheAutoConfiguration() {
        List<String> candidates = new ArrayList<>();
        ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .forEach(candidates::add);
        assertThat(candidates).contains(DatasetsAutoRegistrationAutoConfiguration.class.getName());
    }

    @Test
    void contextBuiltFromTheImportsFileHasTheDatasetsBeans() throws Exception {
        List<Class<?>> classes = new ArrayList<>();
        for (String name : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())) {
            if (name.startsWith("com.hitorro.mesh.datasets.")) {
                classes.add(Class.forName(name));
            }
        }
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(classes.toArray(Class<?>[]::new)))
                .withPropertyValues("hitorro.mesh.datasets.auto-register=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(DatasetRegistry.class);
                    assertThat(ctx).hasSingleBean(Autoregistrar.class);
                });
    }
}
