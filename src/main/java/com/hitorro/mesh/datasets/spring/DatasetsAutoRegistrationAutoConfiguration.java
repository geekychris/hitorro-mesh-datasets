/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.spring;

import com.hitorro.mesh.datasets.registry.Autoregistrar;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.registry.MeshRegistrar;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Auto-configures the datasets registry, mesh registrar, and startup runner.
 *
 * <p>When Spring Boot boots a driver-app that has hitorro-mesh-datasets on
 * its classpath, this class:</p>
 * <ol>
 *   <li>Builds a {@link DatasetRegistry}, loading every bundled manifest.</li>
 *   <li>Builds a {@link MeshRegistrar} pointed at the configured driver URL
 *       (defaults to {@code http://localhost:8085}).</li>
 *   <li>Registers an {@link ApplicationRunner} that, on startup, scans
 *       {@code $HITORRO_DATASETS_HOME} and posts each installed dataset to
 *       the driver.</li>
 * </ol>
 *
 * <p>Disable with {@code hitorro.mesh.datasets.auto-register=false} — the
 * beans are still created (host apps that want to trigger registration
 * manually can still {@code @Autowired} them), but the startup runner is
 * skipped.</p>
 *
 * <p>Order: {@link Ordered#LOWEST_PRECEDENCE} minus a nudge, so any custom
 * pre-registration wiring (auth, schema hooks, feature flags) gets a chance
 * to run first.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(DatasetsAutoRegistrationProperties.class)
public class DatasetsAutoRegistrationAutoConfiguration {

    // System.Logger picks up SLF4J via the JDK's ServiceLoader-based adapter
    // when it's on the classpath, and falls back to java.util.logging when
    // not — so we get real logging in a Spring Boot host without pulling in
    // an explicit slf4j-api compile dependency.
    private static final System.Logger log =
            System.getLogger(DatasetsAutoRegistrationAutoConfiguration.class.getName());

    @Bean
    @ConditionalOnMissingBean
    public DatasetRegistry datasetRegistry() {
        return new DatasetRegistry().loadBundled();
    }

    @Bean
    @ConditionalOnMissingBean
    public MeshRegistrar meshRegistrar(DatasetsAutoRegistrationProperties props) {
        String url = System.getenv().getOrDefault("MESH_DRIVER_URL", props.getDriverUrl());
        return new MeshRegistrar(url);
    }

    @Bean
    @ConditionalOnMissingBean
    public Autoregistrar autoregistrar(DatasetRegistry registry,
                                       MeshRegistrar mesh,
                                       DatasetsAutoRegistrationProperties props) {
        return new Autoregistrar(registry, mesh, props.getSkip());
    }

    @Bean
    @ConditionalOnProperty(prefix = "hitorro.mesh.datasets",
                           name = "auto-register", havingValue = "true", matchIfMissing = true)
    @Order(Ordered.LOWEST_PRECEDENCE - 10)
    public ApplicationRunner datasetsAutoRegistrationRunner(
            Autoregistrar auto,
            DatasetsAutoRegistrationProperties props) {

        return args -> {
            log.log(System.Logger.Level.INFO,
                    "[datasets] scanning " + DatasetRegistry.installedHome()
                            + " for installed manifests");
            Autoregistrar.Report report = auto.registerAllInstalled();
            log.log(System.Logger.Level.INFO, "[datasets] " + report.summary());
            if (props.isFailOnError() && !report.allOk()) {
                throw new IllegalStateException(
                        "dataset auto-registration failed: " + report.failed());
            }
        };
    }
}
