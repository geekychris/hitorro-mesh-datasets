/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.cli;

import com.hitorro.mesh.datasets.registry.Autoregistrar;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.registry.MeshRegistrar;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Standalone entry point: scan installed datasets and register them with a
 * driver. Callable via {@code java -cp}, {@code mvn exec:java}, or the
 * {@code scripts/register-installed.sh} wrapper.
 *
 * <p>Usage:</p>
 * <pre>
 *   register-installed [--driver URL] [--skip id[,id...]]
 * </pre>
 *
 * <p>Driver URL defaults to {@code $MESH_DRIVER_URL} then
 * {@code http://localhost:8085}. Exit codes: 0 on complete success, 1 if
 * any dataset failed to register.</p>
 */
public final class RegisterInstalledCli {

    private RegisterInstalledCli() { }

    public static void main(String[] args) {
        String driverUrl = System.getenv().getOrDefault("MESH_DRIVER_URL", "http://localhost:8085");
        Set<String> skip = new LinkedHashSet<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--driver": driverUrl = args[++i]; break;
                case "--skip":   skip.addAll(Arrays.asList(args[++i].split(","))); break;
                case "-h":
                case "--help":   usage(); return;
                default:
                    System.err.println("unknown arg: " + args[i]);
                    usage();
                    System.exit(2);
            }
        }

        DatasetRegistry registry = new DatasetRegistry().loadBundled();
        MeshRegistrar mesh = new MeshRegistrar(driverUrl);
        Autoregistrar auto = new Autoregistrar(registry, mesh, skip);

        System.out.println("[datasets] driver=" + driverUrl + " scanning "
                + DatasetRegistry.installedHome());
        Autoregistrar.Report report = auto.registerAllInstalled();
        System.out.println("[datasets] " + report.summary());
        System.exit(report.allOk() ? 0 : 1);
    }

    private static void usage() {
        System.err.println("usage: register-installed [--driver URL] [--skip id[,id...]]");
        System.err.println("  --driver   driver base URL (default: $MESH_DRIVER_URL or http://localhost:8085)");
        System.err.println("  --skip     comma-separated dataset ids to leave alone");
    }
}
