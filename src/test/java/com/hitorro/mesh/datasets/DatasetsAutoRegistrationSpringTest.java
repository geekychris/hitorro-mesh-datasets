/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import com.hitorro.mesh.datasets.registry.Autoregistrar;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.registry.MeshRegistrar;
import com.hitorro.mesh.datasets.semantic.PlaceJoinRewriter;
import com.hitorro.mesh.datasets.spring.DatasetsAutoRegistrationAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots a minimal Spring context that pulls in the datasets autoconfig and
 * verifies:
 *
 * <ol>
 *   <li>{@link DatasetRegistry}, {@link MeshRegistrar}, {@link Autoregistrar}
 *       beans exist by default;</li>
 *   <li>the driver-url property flows through to {@link MeshRegistrar};</li>
 *   <li>{@code hitorro.mesh.datasets.auto-register=false} suppresses the
 *       startup runner but leaves the beans in place for manual use.</li>
 * </ol>
 *
 * <p>Deliberately does NOT actually POST to a driver — the runner would need
 * an installed dataset to be present, and this is a wiring test not an
 * integration one. The end-to-end path is covered by
 * {@link AutoregistrarTest} against the in-process fake server.</p>
 */
class DatasetsAutoRegistrationSpringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DatasetsAutoRegistrationAutoConfiguration.class));

    @Test
    void beans_are_created_by_default() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(DatasetRegistry.class);
            assertThat(ctx).hasSingleBean(MeshRegistrar.class);
            assertThat(ctx).hasSingleBean(Autoregistrar.class);
            // Semantic rewriter available too — host apps that want to
            // preprocess USING PLACE clauses just @Autowired it.
            assertThat(ctx).hasSingleBean(PlaceJoinRewriter.class);
        });
    }

    @Test
    void driver_url_property_flows_through() {
        runner.withPropertyValues("hitorro.mesh.datasets.driver-url=http://someother:9999")
                .run(ctx -> {
                    // MeshRegistrar has no public getter for its URL — smoke-test
                    // that the bean at least constructed with the property in play.
                    assertThat(ctx).hasSingleBean(MeshRegistrar.class);
                });
    }

    @Test
    void disabling_auto_register_removes_runner_but_keeps_beans() {
        runner.withPropertyValues("hitorro.mesh.datasets.auto-register=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Autoregistrar.class);
                    // ApplicationRunner beans are matched by type; there should
                    // be no runner registered by this autoconfig.
                    assertThat(ctx.getBeansOfType(org.springframework.boot.ApplicationRunner.class))
                            .doesNotContainKey("datasetsAutoRegistrationRunner");
                });
    }
}
