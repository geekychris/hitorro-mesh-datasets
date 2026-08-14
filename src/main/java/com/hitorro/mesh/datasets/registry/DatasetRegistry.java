/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.registry;

import com.hitorro.mesh.datasets.loader.ManifestLoader;
import com.hitorro.mesh.datasets.model.Manifest;
import com.hitorro.mesh.datasets.model.Relationship;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory catalog of every dataset manifest known to this process.
 *
 * <p>Populated at startup from the bundled resources in {@code /manifests/}
 * and optionally augmented at runtime via {@link #register(Manifest)} for
 * datasets installed off-tree.</p>
 *
 * <p>The registry is the piece the query planner will eventually consult to
 * resolve {@code USING PLACE} / {@code USING ENTITY} — it walks the graph
 * of manifests looking for a path of identifier equivalences from source
 * to target. For now it just holds and searches the metadata.</p>
 */
public final class DatasetRegistry {

    private final Map<String, Manifest> byId = new LinkedHashMap<>();

    /** Load every bundled manifest. Errors on individual files are logged, not thrown. */
    public synchronized DatasetRegistry loadBundled() {
        for (String id : ManifestLoader.listBundled()) {
            try {
                byId.put(id, ManifestLoader.loadBundled(id));
            } catch (IOException e) {
                System.err.println("[datasets] failed to load bundled manifest " + id + ": " + e.getMessage());
            }
        }
        return this;
    }

    public synchronized void register(Manifest m) {
        byId.put(m.id(), m);
    }

    public synchronized Manifest get(String id) {
        return byId.get(id);
    }

    public synchronized Collection<Manifest> all() {
        return new ArrayList<>(byId.values());
    }

    /**
     * Find every manifest whose identifier {@code produces} or {@code maps}
     * list contains the given namespace. This is how the planner locates
     * candidate join targets given only a namespace name.
     */
    public synchronized List<Manifest> byIdentifier(String namespace) {
        List<Manifest> out = new ArrayList<>();
        for (Manifest m : byId.values()) {
            if (m.identifiers() == null) continue;
            List<String> prod = m.identifiers().produces();
            List<String> maps = m.identifiers().maps();
            if ((prod != null && prod.contains(namespace))
                    || (maps != null && maps.contains(namespace))) {
                out.add(m);
            }
        }
        return out;
    }

    /**
     * Every direct relationship this dataset declares. Useful for graph
     * traversal (BFS across relationships when resolving a semantic join).
     */
    public synchronized List<Relationship> outboundFrom(String datasetId) {
        Manifest m = byId.get(datasetId);
        if (m == null || m.relationships() == null) return List.of();
        return new ArrayList<>(m.relationships());
    }
}
