/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.cli;

import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.semantic.PlaceJoinRewriter;
import com.hitorro.mesh.datasets.semantic.SemanticJoinException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Reads SQL from stdin, rewrites {@code USING PLACE} joins into concrete
 * ON clauses using the bundled + installed manifests, prints the result.
 *
 * <p>Usage:</p>
 * <pre>
 *   echo "SELECT * FROM cities gn JOIN countries USING PLACE" \
 *     | mvn -q exec:java -Dexec.mainClass=com.hitorro.mesh.datasets.cli.RewriteSqlCli
 * </pre>
 *
 * <p>The shipped {@code scripts/rewrite-sql.sh} wraps this so daily use is
 * just: {@code echo "..." | ./scripts/rewrite-sql.sh}.</p>
 */
public final class RewriteSqlCli {

    private RewriteSqlCli() { }

    public static void main(String[] args) throws Exception {
        String sql;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            sql = r.lines().collect(Collectors.joining("\n"));
        }
        if (sql.isBlank()) {
            System.err.println("usage: pipe SQL to stdin; rewritten SQL prints to stdout");
            System.exit(2);
        }

        DatasetRegistry registry = new DatasetRegistry().loadBundled();
        registry.scanInstalled();   // pick up whatever's under $HITORRO_DATASETS_HOME too
        PlaceJoinRewriter rewriter = new PlaceJoinRewriter(registry);

        try {
            System.out.println(rewriter.rewrite(sql));
        } catch (SemanticJoinException e) {
            System.err.println("[rewrite] " + e.getMessage());
            System.exit(1);
        }
    }
}
