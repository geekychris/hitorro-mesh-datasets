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

        StringBuilder out = new StringBuilder();
        Matcher joinM = JOIN_USING_PLACE.matcher(sql);
        int cursor = 0;

        while (joinM.find()) {
            String table = joinM.group(1);
            String rawAlias = stripKeyword(joinM.group(2));
            TableRef target = tableRefOrThrow(table, rawAlias);

            String onClause = resolve(priors, target);

            out.append(sql, cursor, joinM.start());
            out.append("JOIN ").append(table);
            if (rawAlias != null) out.append(" ").append(rawAlias);
            out.append(" ON ").append(onClause);
            cursor = joinM.end();

            priors.add(target);
        }
        out.append(sql, cursor, sql.length());
        return out.toString();
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

    /**
     * Find a prior table with an EXACT_ID relationship to {@code target}
     * (or vice versa) and build the ON clause.
     */
    private String resolve(List<TableRef> priors, TableRef target) {
        for (TableRef prior : priors) {
            String on = tryBuildOn(prior, target);
            if (on != null) return on;
        }
        String priorList = priors.stream().map(r -> r.tableName).toList().toString();
        throw new SemanticJoinException(
                "no EXACT_ID relationship declared between " + target.tableName
                + " and any prior table " + priorList + " — either add a "
                + "relationships: entry to one of the manifests, or write "
                + "the JOIN with an explicit ON clause.");
    }

    /** @return an ON clause body, or null if no relationship links the pair. */
    private String tryBuildOn(TableRef source, TableRef target) {
        // Forward: source declares a relationship to target.
        Relationship fwd = findExactIdTo(source.manifest, target.manifest.id());
        if (fwd != null) {
            String srcCol = singleVia(fwd, source.manifest.id());
            String tgtCol = pickTargetColumn(source.manifest, srcCol, target.manifest);
            return castingEqui(source, srcCol, target, tgtCol);
        }
        // Reverse: target declares a relationship to source.
        Relationship rev = findExactIdTo(target.manifest, source.manifest.id());
        if (rev != null) {
            String tgtCol = singleVia(rev, target.manifest.id());
            String srcCol = pickTargetColumn(target.manifest, tgtCol, source.manifest);
            return castingEqui(source, srcCol, target, tgtCol);
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

    private static Relationship findExactIdTo(Manifest source, String targetId) {
        if (source.relationships() == null) return null;
        for (Relationship r : source.relationships()) {
            if (targetId.equals(r.target()) && r.kind() == RelationshipKind.EXACT_ID) return r;
        }
        return null;
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
