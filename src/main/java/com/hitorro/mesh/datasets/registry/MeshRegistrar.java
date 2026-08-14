/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.mesh.datasets.model.Manifest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers a locally-installed dataset with a running mesh driver via
 * {@code POST /mesh/tables} (or {@code /mesh/broadcast-tables}).
 *
 * <p>The install scripts materialise the raw source into NDJSON + a JVS type
 * file under {@code $HITORRO_DATASETS_HOME/<dataset-id>/}. This class then
 * announces the resulting partitions to the driver so agents can serve them.</p>
 *
 * <p>Wire contract matches the driver's runtime registration endpoints
 * (see {@code MeshRestController.registerTable}):</p>
 * <pre>
 * POST /mesh/tables
 * {
 *   "name": "geonames_cities",
 *   "typeJsonResource": "file:/.../types/geonames_cities.json",
 *   "partitions": [
 *     { "key": "cities15000",
 *       "requiredCapabilities": ["jvssql", "partition:geonames_cities:cities15000"] }
 *   ]
 * }
 * </pre>
 */
public final class MeshRegistrar {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String driverBaseUrl;
    private final HttpClient http;

    public MeshRegistrar(String driverBaseUrl) {
        // trim trailing slash so we can concatenate paths
        this.driverBaseUrl = driverBaseUrl.endsWith("/")
                ? driverBaseUrl.substring(0, driverBaseUrl.length() - 1)
                : driverBaseUrl;
        this.http = HttpClient.newHttpClient();
    }

    /**
     * Announce a distributed dataset. Agents matching the capabilities in each
     * partition must already be running with the NDJSON pre-loaded — the driver
     * only holds metadata, not data.
     *
     * @param manifest      the loaded manifest (for name + record type)
     * @param typeJsonPath  path to the JVS type file emitted by the install script
     * @param partitionKeys logical partition names (e.g. {@code ["cities15000"]},
     *                      or one-per-country if the install script split the data)
     */
    public void registerDistributed(Manifest manifest, Path typeJsonPath, List<String> partitionKeys)
            throws IOException, InterruptedException {

        String tableName = tableName(manifest);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", tableName);
        // The driver's registerTable endpoint expects the JVS type inline as
        // a JSON string, not a file:// URL — the RegisterTableRequest.typeJson
        // field. Read the file's bytes and stringify them.
        body.put("typeJson", Files.readString(typeJsonPath));

        List<Map<String, Object>> parts = partitionKeys.stream().map(k -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("key", k);
            p.put("requiredCapabilities", List.of("jvssql", "partition:" + tableName + ":" + k));
            return p;
        }).toList();
        body.put("partitions", parts);

        post("/mesh/tables", body);
    }

    /**
     * Announce a small dimension dataset that every agent pre-loads. Country
     * info is the canonical example — a few hundred rows, joined into every
     * per-city query.
     */
    public void registerBroadcast(Manifest manifest) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", tableName(manifest));
        post("/mesh/broadcast-tables", body);
    }

    /**
     * Extra registration that goes alongside {@link #registerBroadcast}:
     * declares the same broadcast dataset as a distributed table with a
     * single synthetic partition that any jvssql agent can serve.
     *
     * <p>Why this matters: the mesh's planner requires the {@code FROM}
     * table to appear in the distributed set. Broadcast-only tables can
     * only be JOIN targets. Registering the broadcast dataset as
     * distributed-single-partition too lets users run
     * {@code SELECT * FROM wikidata_cities} directly — the standalone
     * "let me look at this dataset" flow the UI's Datasets tab wants.</p>
     *
     * <p>Every jvssql agent that carries the broadcast data can serve
     * this partition (capability = {@code [jvssql]}) because
     * {@code TaskExecutor} already registers all
     * {@link com.hitorro.mesh.agent.AgentConfig#broadcastTables()} into
     * every task's engine builder. The dual registration is small — a
     * table name plus one partition entry — and doesn't duplicate data
     * anywhere.</p>
     */
    public void registerBroadcastAsDistributed(Manifest manifest, Path typeJsonPath)
            throws IOException, InterruptedException {

        String tableName = tableName(manifest);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", tableName);
        body.put("typeJson", Files.readString(typeJsonPath));

        Map<String, Object> partition = new LinkedHashMap<>();
        partition.put("key", "broadcast");
        // Only [jvssql] — no per-partition capability, so any jvssql agent
        // can serve it. That's exactly the "runs anywhere" semantic we want
        // for a fully-replicated broadcast dataset.
        partition.put("requiredCapabilities", List.of("jvssql"));
        body.put("partitions", List.of(partition));

        post("/mesh/tables", body);
    }

    public void unregister(Manifest manifest) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(driverBaseUrl + "/mesh/tables/" + tableName(manifest)))
                .DELETE()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("driver rejected DELETE: " + resp.statusCode() + " " + resp.body());
        }
    }

    private void post(String path, Object body) throws IOException, InterruptedException {
        byte[] payload = JSON.writeValueAsBytes(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(driverBaseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("driver rejected POST " + path + ": "
                    + resp.statusCode() + " " + resp.body());
        }
    }

    /**
     * Manifest ids use kebab-case ({@code geonames-cities15000}) but SQL
     * identifiers need underscores. Applied consistently on both driver and
     * agent sides so the two agree on the table name.
     */
    static String tableName(Manifest m) {
        return m.id().replace('-', '_');
    }
}
