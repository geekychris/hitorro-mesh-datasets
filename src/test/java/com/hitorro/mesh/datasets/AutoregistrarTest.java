/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.mesh.datasets.registry.Autoregistrar;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;
import com.hitorro.mesh.datasets.registry.MeshRegistrar;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the auto-registration hook against an in-process
 * fake driver. Uses the JDK's built-in HttpServer so no extra test-only
 * dependency is needed. The install root is redirected via the
 * {@code hitorro.datasets.home} system property.
 */
class AutoregistrarTest {

    private HttpServer server;
    private int port;
    private final ConcurrentMap<String, String> lastBodyByPath = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void up() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mesh/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes());
            lastBodyByPath.put(ex.getRequestURI().getPath(), body);
            byte[] resp = ("{\"ok\":true,\"path\":\"" + ex.getRequestURI().getPath() + "\"}").getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void down() {
        server.stop(0);
        System.clearProperty("hitorro.datasets.home");
    }

    @Test
    void autoregister_walks_installed_root_and_posts_each_manifest(@TempDir Path home) throws Exception {
        installFakeManifest(home, "geonames-cities15000", "partitionBy: country_code");
        installFakeManifest(home, "natural-earth-countries", "partitionBy: null");
        // Both datasets need a type json now — the broadcast one also POSTs
        // to /mesh/tables (the distributed-single-partition side-registration).
        writeFakeType(home, "geonames-cities15000");
        writeFakeType(home, "natural-earth-countries");

        System.setProperty("hitorro.datasets.home", home.toString());

        DatasetRegistry registry = new DatasetRegistry();
        MeshRegistrar mesh = new MeshRegistrar("http://127.0.0.1:" + port);
        Autoregistrar auto = new Autoregistrar(registry, mesh);
        Autoregistrar.Report report = auto.registerAllInstalled();

        assertThat(report.allOk())
                .withFailMessage("expected all ok, got: %s", report.summary())
                .isTrue();
        assertThat(report.registered())
                .containsExactlyInAnyOrder("geonames-cities15000", "natural-earth-countries");

        // Broadcast POST landed with the right table name (kebab → snake case).
        String broadcastBody = lastBodyByPath.get("/mesh/broadcast-tables");
        assertThat(broadcastBody).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> broadcast = json.readValue(broadcastBody, Map.class);
        assertThat(broadcast).containsEntry("name", "natural_earth_countries");

        // Every broadcast dataset also gets a distributed-single-partition
        // registration so SELECT * FROM works standalone. Distinguish which
        // /mesh/tables POST is which by name.
        // (The fake driver captures ONE body per path — last write wins. With
        // both natural-earth and geonames-cities registering as distributed,
        // the last one seen is whichever the map iteration order emits last.)
        String distBody = lastBodyByPath.get("/mesh/tables");
        assertThat(distBody).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> dist = json.readValue(distBody, Map.class);
        assertThat(dist).containsKey("name");
        assertThat((String) dist.get("typeJsonResource")).startsWith("file:");
        assertThat((List<?>) dist.get("partitions")).hasSize(1);
    }

    @Test
    void skip_ids_are_left_alone(@TempDir Path home) throws Exception {
        installFakeManifest(home, "natural-earth-countries", "partitionBy: null");
        installFakeManifest(home, "docs", "partitionBy: null");
        writeFakeType(home, "natural-earth-countries");
        writeFakeType(home, "docs");

        System.setProperty("hitorro.datasets.home", home.toString());

        Autoregistrar auto = new Autoregistrar(
                new DatasetRegistry(),
                new MeshRegistrar("http://127.0.0.1:" + port),
                Set.of("docs"));
        Autoregistrar.Report report = auto.registerAllInstalled();

        assertThat(report.registered()).containsExactly("natural-earth-countries");
        assertThat(report.skipped()).containsExactly("docs");
        assertThat(report.failed()).isEmpty();
    }

    // ------------------------------------------------------------------

    /** Every broadcast/distributed dataset needs a type json for
     *  Autoregistrar's file-existence check. */
    private static void writeFakeType(Path home, String id) throws IOException {
        String tableName = id.replace('-', '_');
        Path typesDir = Files.createDirectories(home.resolve(id).resolve("types"));
        Files.writeString(typesDir.resolve(tableName + ".json"),
                "{\"name\":\"" + tableName + "\",\"fields\":[]}");
    }

    private static void installFakeManifest(Path home, String id, String extra) throws IOException {
        Path dir = Files.createDirectories(home.resolve(id));
        String yaml = ""
                + "id: " + id + "\n"
                + "title: " + id + "\n"
                + "version: '1.0'\n"
                + "license:\n"
                + "  spdx: CC0-1.0\n"
                + "  attribution: test\n"
                + "  commercialUse: true\n"
                + "  redistribution: true\n"
                + "  attributionRequired: false\n"
                + "  shareAlike: false\n"
                + "  modification: true\n"
                + "source: { type: http, url: http://example, format: ndjson }\n"
                + "record: { type: test, primaryKey: id, fields: [] }\n"
                + extra + "\n";
        Files.writeString(dir.resolve("manifest.yaml"), yaml);
    }
}
