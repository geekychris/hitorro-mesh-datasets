/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.mesh.datasets.semantic;

import com.hitorro.mesh.datasets.model.FieldSpec;
import com.hitorro.mesh.datasets.model.Manifest;
import com.hitorro.mesh.datasets.model.RecordSpec;
import com.hitorro.mesh.datasets.model.Relationship;
import com.hitorro.mesh.datasets.model.RelationshipKind;
import com.hitorro.mesh.datasets.registry.DatasetRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites {@code USING PLACE} joins into concrete {@code ON} clauses by
 * consulting the {@link DatasetRegistry}.
 *
 * <p>Given:</p>
 * <pre>
 * SELECT ...
 * FROM geonames_cities15000 gn
 * JOIN natural_earth_countries ne USING PLACE
 * </pre>
 *
 * <p>The rewriter finds {@code gn}'s manifest ({@code geonames-cities15000}),
 * finds {@code ne}'s manifest ({@code natural-earth-countries}), locates an
 * EXACT_ID relationship declared in either direction, and emits:</p>
 * <pre>
 * SELECT ...
 * FROM geonames_cities15000 gn
 * JOIN natural_earth_countries ne ON gn.country_code = ne.iso_a2
 * </pre>
 *
 * <p>Each subsequent JOIN can reference any prior table (not just the
 * immediately preceding one), so this works:</p>
 * <pre>
 * FROM geonames_cities15000 gn
 * JOIN natural_earth_countries ne USING PLACE
 * JOIN wikidata_cities wd USING PLACE          -- resolves against gn, not ne
 * </pre>
 *
 * <p>Type mismatches between source and target join columns get an
 * automatic {@code CAST} wrapper — critical for {@code wikidata_cities.geonames_id}
 * (string) → {@code geonames_cities15000.geonameid} (bigint).</p>
 *
 * <h2>MVP scope</h2>
 * <ul>
 *   <li>{@link RelationshipKind#EXACT_ID} only. SPATIAL and PROBABILISTIC
 *       joins throw with a "not yet supported" message so the SQL author
 *       knows to fall back to an explicit clause.</li>
 *   <li>Single-column joins ({@code via: [col]}). Multi-column (composite
 *       keys) throws with a clear message.</li>
 *   <li>Regex-based JOIN detection. Robust for the well-formed SQL the
 *       existing mesh emits; not a full SQL parser. Comments and string
 *       literals containing "USING PLACE" would be misinterpreted — the
 *       jvssql planner extension in the roadmap is the "real" fix.</li>
 * </ul>
 */
public final class PlaceJoinRewriter {

    // Captures: JOIN <table> [AS] <alias>? USING PLACE
    // Alias is optional — falls back to the table name when absent.
    private static final Pattern JOIN_USING_PLACE = Pattern.compile(
            "\\bJOIN\\s+(\\w+)(?:\\s+(?:AS\\s+)?(\\w+))?\\s+USING\\s+PLACE\\b",
            Pattern.CASE_INSENSITIVE);

    // First table in the FROM clause. Same optional-alias rule.
    private static final Pattern FROM_TABLE = Pattern.compile(
            "\\bFROM\\s+(\\w+)(?:\\s+(?:AS\\s+)?(\\w+))?",
            Pattern.CASE_INSENSITIVE);

    // SQL keywords that must not be captured as a table alias by the greedy
    // "(\\w+)? optional-alias" group in the patterns below. If the token
    // that follows a table name is any of these, the alias is absent and
    // the table name itself doubles as the alias.
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS",
            "ON", "USING", "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT",
            "OFFSET", "UNION", "INTERSECT", "EXCEPT");

    private final DatasetRegistry registry;

    public PlaceJoinRewriter(DatasetRegistry registry) {
        this.registry = registry;
    }

    /** Rewrite every USING PLACE in the SQL. If none present, returns the SQL unchanged. */
    public String rewrite(String sql) {
        if (sql == null || !sql.toUpperCase(Locale.ROOT).contains("USING PLACE")) return sql;

        Matcher fromM = FROM_TABLE.matcher(sql);
        if (!fromM.find()) {
            throw new SemanticJoinException(
                    "USING PLACE requires a FROM clause (no FROM found in query)");
        }

        List<TableRef> priors = new ArrayList<>();
        priors.add(tableRefOrThrow(fromM.group(1), stripKeyword(fromM.group(2))));

        // Accumulate SPATIAL bbox predicates as we scan JOINs; they get
        // spliced into the WHERE clause after all replacements finish
        // because jvssql phase-1 requires an equijoin key in every
        // ON clause. CROSS JOIN + WHERE bbox is the closest correct
        // rewrite that today's engine can dispatch.
        List<String> spatialWherePreds = new ArrayList<>();

        StringBuilder out = new StringBuilder();
        Matcher joinM = JOIN_USING_PLACE.matcher(sql);
        int cursor = 0;

        while (joinM.find()) {
            String table = joinM.group(1);
            String rawAlias = stripKeyword(joinM.group(2));
            TableRef target = tableRefOrThrow(table, rawAlias);

            Resolved r = resolveWithKind(priors, target);

            out.append(sql, cursor, joinM.start());
            if (r.spatial) {
                // CROSS JOIN + WHERE bbox — the only shape today's
                // phase-1 planner will actually dispatch for a range join.
                out.append("CROSS JOIN ").append(table);
                if (rawAlias != null) out.append(" ").append(rawAlias);
                spatialWherePreds.add(r.clause);
            } else {
                out.append("JOIN ").append(table);
                if (rawAlias != null) out.append(" ").append(rawAlias);
                out.append(" ON ").append(r.clause);
            }
            cursor = joinM.end();

            priors.add(target);
        }
        out.append(sql, cursor, sql.length());
        String rewritten = out.toString();

        if (!spatialWherePreds.isEmpty()) {
            rewritten = injectIntoWhere(rewritten, spatialWherePreds);
        }
        return rewritten;
    }

    /**
     * Splice the collected SPATIAL bbox predicates into the query's WHERE
     * clause. If no WHERE exists, append one before ORDER BY / GROUP BY /
     * LIMIT / etc; if none of those exist either, append at end.
     */
    private static String injectIntoWhere(String sql, List<String> preds) {
        String joined = String.join(" AND ", preds);
        Matcher whereM = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE).matcher(sql);
        if (whereM.find()) {
            int after = whereM.end();
            return sql.substring(0, after) + " (" + joined + ") AND"
                 + sql.substring(after);
        }
        // No WHERE — find the first clause that comes after FROM/JOIN so
        // we can slot WHERE in before it (Calcite parses left-to-right).
        Matcher tailM = Pattern.compile(
                "\\b(GROUP\\s+BY|ORDER\\s+BY|HAVING|LIMIT|OFFSET|UNION|INTERSECT|EXCEPT)\\b",
                Pattern.CASE_INSENSITIVE).matcher(sql);
        if (tailM.find()) {
            return sql.substring(0, tailM.start()) + "WHERE " + joined + " "
                 + sql.substring(tailM.start());
        }
        return sql + " WHERE " + joined;
    }

    /** Small record so the JOIN-scan loop can distinguish spatial from exact. */
    private record Resolved(String clause, boolean spatial) { }

    private Resolved resolveWithKind(List<TableRef> priors, TableRef target) {
        // EXACT_ID pass, then SPATIAL — same order as resolve() but this
        // variant tells the caller which one landed so it can pick JOIN
        // shape (ON vs CROSS JOIN + WHERE).
        for (TableRef prior : priors) {
            String c = tryBuildExactId(prior, target);
            if (c != null) return new Resolved(c, false);
        }
        for (TableRef prior : priors) {
            String c = tryBuildSpatial(prior, target);
            if (c != null) return new Resolved(c, true);
        }
        String priorList = priors.stream().map(r -> r.tableName).toList().toString();
        throw new SemanticJoinException(
                "no EXACT_ID or SPATIAL relationship declared between " + target.tableName
                + " and any prior table " + priorList + " — either add a "
                + "relationships: entry to one of the manifests, or write "
                + "the JOIN with an explicit ON clause.");
    }

    /**
     * The alias-capturing regex group greedily eats the next {@code \w+}, but
     * that next word may actually be a SQL keyword like {@code JOIN} or
     * {@code WHERE} — meaning the alias was absent. Returns {@code null} in
     * that case so the caller falls back to the table name.
     */
    private static String stripKeyword(String candidate) {
        if (candidate == null) return null;
        return SQL_KEYWORDS.contains(candidate.toUpperCase(Locale.ROOT)) ? null : candidate;
    }

    // ------------------------------------------------------------------


    /** @return an EXACT_ID ON clause body, or null if none exists between the pair. */
    private String tryBuildExactId(TableRef source, TableRef target) {
        // Forward: source declares a relationship to target.
        Relationship fwd = findRelationshipTo(source.manifest, target.manifest.id(), RelationshipKind.EXACT_ID);
        if (fwd != null) {
            String srcCol = singleVia(fwd, source.manifest.id());
            String tgtCol = pickTargetColumn(source.manifest, srcCol, target.manifest);
            return castingEqui(source, srcCol, target, tgtCol);
        }
        // Reverse: target declares a relationship to source.
        Relationship rev = findRelationshipTo(target.manifest, source.manifest.id(), RelationshipKind.EXACT_ID);
        if (rev != null) {
            String tgtCol = singleVia(rev, target.manifest.id());
            String srcCol = pickTargetColumn(target.manifest, tgtCol, source.manifest);
            return castingEqui(source, srcCol, target, tgtCol);
        }
        return null;
    }

    /**
     * @return a SPATIAL bounding-box ON clause body, or null if no SPATIAL
     *         relationship links the pair. MVP — see {@link #buildSpatial}
     *         for the semantics.
     */
    private String tryBuildSpatial(TableRef source, TableRef target) {
        Relationship sfwd = findRelationshipTo(source.manifest, target.manifest.id(), RelationshipKind.SPATIAL);
        if (sfwd != null) return buildSpatial(source, sfwd, target);
        Relationship srev = findRelationshipTo(target.manifest, source.manifest.id(), RelationshipKind.SPATIAL);
        if (srev != null) return buildSpatial(source, srev, target);
        return null;
    }

    /**
     * Emit a bounding-box join for a SPATIAL relationship.
     *
     * <p>MVP: assumes target carries four columns with roles
     * {@code geo.bbox.min_lat}, {@code geo.bbox.max_lat},
     * {@code geo.bbox.min_lon}, {@code geo.bbox.max_lon} — the
     * install-time-computed envelope of each row's polygon. Emits
     * {@code src.lat BETWEEN tgt.min_lat AND tgt.max_lat AND
     * src.lon BETWEEN tgt.min_lon AND tgt.max_lon}.</p>
     *
     * <p>Known limits (see also the docs):</p>
     * <ul>
     *   <li>Bounding-box only — a point inside the bbox but outside the
     *       polygon still matches. Fine for coarse country attribution;
     *       wrong for anything requiring true PIP.</li>
     *   <li>Countries crossing the antimeridian get planet-spanning bboxes.
     *       Every earthquake worldwide will match Fiji / Russia / USA.</li>
     *   <li>Multiple polygons per country get one merged bbox. Kiribati's
     *       three island groups become one huge Pacific rectangle.</li>
     * </ul>
     *
     * <p>The right long-term fix is a spatial index at the agent (JTS
     * R-tree or S2 cells) — this MVP is what a preprocessor can do without
     * touching the mesh's execution layer. It's often good enough for
     * demoing the SPATIAL surface and testing the join graph.</p>
     */
    private static String buildSpatial(TableRef source, Relationship rel, TableRef target) {
        List<String> via = rel.via();
        if (via == null || via.size() != 2) {
            throw new SemanticJoinException(
                    "SPATIAL relationship on " + rel.target()
                    + " needs exactly two `via` columns (lat, lon or lon, lat) — got " + via);
        }

        // Convention on the source side: via is [latColumn, lonColumn].
        // We validate by checking roles when available.
        String latSrc = via.get(0);
        String lonSrc = via.get(1);
        // Swap if the first via column is clearly the longitude.
        String r0 = roleOf(source.manifest.record(), via.get(0));
        String r1 = roleOf(source.manifest.record(), via.get(1));
        if ("geo.lon".equals(r0) && "geo.lat".equals(r1)) {
            latSrc = via.get(1);
            lonSrc = via.get(0);
        }

        // Target-side bbox columns by role.
        String minLat = findFieldWithRole(target.manifest.record(), "geo.bbox.min_lat");
        String maxLat = findFieldWithRole(target.manifest.record(), "geo.bbox.max_lat");
        String minLon = findFieldWithRole(target.manifest.record(), "geo.bbox.min_lon");
        String maxLon = findFieldWithRole(target.manifest.record(), "geo.bbox.max_lon");
        if (minLat == null || maxLat == null || minLon == null || maxLon == null) {
            throw new SemanticJoinException(
                    "SPATIAL join to " + target.tableName + " needs role-tagged "
                    + "bbox columns (geo.bbox.{min,max}_{lat,lon}) — install "
                    + "scripts for polygon datasets should emit them from "
                    + "geometry. Fall back to an explicit ST_Contains "
                    + "predicate until a spatial index lands.");
        }

        // Returned as the bbox predicate body — the caller lifts it into
        // a WHERE clause via a CROSS JOIN, because jvssql phase-1 rejects
        // ON conditions with no equijoin key. The two BETWEEN predicates
        // together are what actually prune to the containing polygon(s).
        return source.alias + "." + latSrc + " BETWEEN "
             + target.alias + "." + minLat + " AND " + target.alias + "." + maxLat
             + " AND "
             + source.alias + "." + lonSrc + " BETWEEN "
             + target.alias + "." + minLon + " AND " + target.alias + "." + maxLon;
    }

    private static Relationship findRelationshipTo(Manifest source, String targetId, RelationshipKind kind) {
        if (source.relationships() == null) return null;
        for (Relationship r : source.relationships()) {
            if (targetId.equals(r.target()) && r.kind() == kind) return r;
        }
        return null;
    }

    /**
     * Pick the target-side column for a join.
     *
     * <p>Prefers <em>identifier-role matching</em> when both sides declare
     * the same namespace: if the source field has {@code role: id.iso3166alpha2}
     * and the target has a field with the same role, that's the join column,
     * regardless of primary keys. This is the mechanism that gets
     * {@code wikidata_cities.country_iso → natural_earth_countries.iso_a2}
     * right (the target's primaryKey is {@code iso_a3}, which would be wrong).</p>
     *
     * <p>Falls back to the target's primaryKey when no matching role is
     * declared — the classical "foreign key into a primary key" case that
     * {@code geonames-cities → geonames-country-info} follows.</p>
     */
    private static String pickTargetColumn(Manifest source, String sourceCol, Manifest target) {
        String srcRole = roleOf(source.record(), sourceCol);
        if (srcRole != null && srcRole.startsWith("id.")) {
            String match = findFieldWithRole(target.record(), srcRole);
            if (match != null) return match;
            // The target's primary key might itself declare only role: id
            // (unqualified). Check whether the pk's semantic namespace matches
            // by looking at the id.* prefix.
            String pk = target.record().primaryKey();
            String pkRole = roleOf(target.record(), pk);
            if (srcRole.equals(pkRole)) return pk;
        }
        return target.record().primaryKey();
    }

    private static String roleOf(RecordSpec spec, String colName) {
        if (spec == null || spec.fields() == null) return null;
        for (FieldSpec f : spec.fields()) {
            if (f.name().equals(colName)) return f.role();
        }
        return null;
    }

    private static String findFieldWithRole(RecordSpec spec, String role) {
        if (spec == null || spec.fields() == null) return null;
        for (FieldSpec f : spec.fields()) {
            if (role.equals(f.role())) return f.name();
        }
        return null;
    }

    /** Build {@code a.x = b.y}, wrapping in CAST if the field types differ. */
    private static String castingEqui(TableRef src, String srcCol,
                                      TableRef tgt, String tgtCol) {
        String srcType = typeOf(src.manifest.record(), srcCol);
        String tgtType = typeOf(tgt.manifest.record(), tgtCol);
        String srcExpr = src.alias + "." + srcCol;
        String tgtExpr = tgt.alias + "." + tgtCol;
        if (srcType != null && tgtType != null && !srcType.equals(tgtType)) {
            // Cast source to target's type so predicate pushdown at the
            // agent side gets a comparable column pair.
            srcExpr = "CAST(" + srcExpr + " AS " + sqlType(tgtType) + ")";
        }
        return srcExpr + " = " + tgtExpr;
    }

    private static String typeOf(RecordSpec spec, String colName) {
        if (spec == null || spec.fields() == null) return null;
        for (FieldSpec f : spec.fields()) {
            if (f.name().equals(colName)) return f.type();
        }
        return null;
    }

    /** JVS type → Calcite SQL type keyword. */
    private static String sqlType(String jvsType) {
        return switch (jvsType) {
            case "core_string" -> "VARCHAR";
            case "core_long"   -> "BIGINT";
            case "core_int"    -> "INTEGER";
            case "core_double" -> "DOUBLE";
            case "core_float"  -> "REAL";
            case "core_bool"   -> "BOOLEAN";
            default            -> "VARCHAR";   // permissive fallback
        };
    }

    private static String singleVia(Relationship r, String manifestId) {
        List<String> via = r.via();
        if (via == null || via.size() != 1) {
            throw new SemanticJoinException(
                    "USING PLACE only supports single-column relationships today; "
                    + manifestId + "'s relationship declares via=" + via
                    + " — use an explicit ON clause for composite keys.");
        }
        return via.get(0);
    }

    private TableRef tableRefOrThrow(String tableName, String alias) {
        Manifest m = registry.byTableName(tableName);
        if (m == null) {
            throw new SemanticJoinException(
                    "USING PLACE requires a bundled or installed manifest for table `"
                    + tableName + "` — none found. Register the dataset first "
                    + "(./scripts/install-*.sh) or use an explicit ON clause.");
        }
        return new TableRef(tableName, (alias == null || alias.isBlank()) ? tableName : alias, m);
    }

    private record TableRef(String tableName, String alias, Manifest manifest) { }
}
