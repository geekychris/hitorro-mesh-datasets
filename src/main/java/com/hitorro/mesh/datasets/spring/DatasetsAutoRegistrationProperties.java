/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuration for the datasets auto-registration hook.
 *
 * <p>Bind under {@code hitorro.mesh.datasets} in {@code application.yml}:</p>
 * <pre>
 * hitorro:
 *   mesh:
 *     datasets:
 *       auto-register: true              # default: true when the module is on the classpath
 *       driver-url: http://localhost:8085
 *       skip:
 *         - docs                          # ids that must not be touched
 *       fail-on-error: false              # true → throw on any registration failure
 * </pre>
 */
@ConfigurationProperties(prefix = "hitorro.mesh.datasets")
public class DatasetsAutoRegistrationProperties {

    /** Whether to scan-and-register at startup. */
    private boolean autoRegister = true;

    /** The driver's base URL. Overridden by {@code MESH_DRIVER_URL} env var. */
    private String driverUrl = "http://localhost:8085";

    /** Dataset ids the auto-registrar must leave alone. */
    private Set<String> skip = new LinkedHashSet<>();

    /** Throw on any per-dataset registration failure (default: log and continue). */
    private boolean failOnError = false;

    public boolean isAutoRegister() { return autoRegister; }
    public void setAutoRegister(boolean v) { this.autoRegister = v; }

    public String getDriverUrl() { return driverUrl; }
    public void setDriverUrl(String v) { this.driverUrl = v; }

    public Set<String> getSkip() { return skip; }
    public void setSkip(Set<String> v) { this.skip = v; }

    public boolean isFailOnError() { return failOnError; }
    public void setFailOnError(boolean v) { this.failOnError = v; }
}
