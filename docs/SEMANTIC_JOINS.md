# Semantic joins — `USING PLACE`

The declared relationships in each manifest aren't documentation. The
`PlaceJoinRewriter` turns them into live SQL: instead of remembering that
`geonames_cities15000.country_code` joins to `geonames_country_info.iso`,
you write:

```sql
FROM geonames_cities15000 gn
JOIN geonames_country_info ci USING PLACE
```

and the rewriter emits the correct `ON` clause using the relationships
declared in the manifests.

## What it produces

Input:
```sql
SELECT gn.name, ne.income_grp
FROM geonames_cities15000 gn
JOIN geonames_country_info ci USING PLACE
JOIN natural_earth_countries ne USING PLACE
WHERE gn.population > 500000
```

Output:
```sql
SELECT gn.name, ne.income_grp
FROM geonames_cities15000 gn
JOIN geonames_country_info ci ON gn.country_code = ci.iso
JOIN natural_earth_countries ne ON ci.iso = ne.iso_a2
WHERE gn.population > 500000
```

Type mismatch gets a CAST automatically — critical for
`wikidata_cities.geonames_id` (string) → `geonames_cities15000.geonameid`
(bigint):

```sql
JOIN geonames_cities15000 gn USING PLACE
-- becomes:
JOIN geonames_cities15000 gn ON CAST(wd.geonames_id AS BIGINT) = gn.geonameid
```

## Resolution rules

For each `JOIN t USING PLACE`, in order:

1. **Look at prior tables** — the `FROM` table plus every earlier `JOIN`
   target. Each `USING PLACE` can reference any of them, not just the
   immediately preceding one.
2. **Forward direction first** — does any prior manifest declare an
   `EXACT_ID` relationship whose `target` is this join's manifest? If so,
   pick the target column (see step 4) and emit
   `<prior_alias>.<prior.via> = <target_alias>.<target_column>`.
3. **Reverse direction** — does the join's manifest declare an `EXACT_ID`
   relationship whose `target` is a prior manifest? If so, pick the target
   column and emit `<prior_alias>.<prior_column> = <target_alias>.<target.via>`.
4. **Pick the target column by matching identifier role first, primaryKey
   second.** If the source field carries `role: id.<namespace>` and the
   target manifest has a field with the same role, that's the join
   column. Otherwise fall back to `target.primaryKey`. This is what gets
   `wikidata_cities.country_iso → natural_earth_countries.iso_a2` right
   (naive "join to primaryKey" would emit `= iso_a3`, which is 3-letter,
   never matches).
5. **Type mismatch** — wrap the source column in
   `CAST(x AS <target_type>)`. Type mapping:
   `core_string→VARCHAR`, `core_long→BIGINT`, `core_int→INTEGER`,
   `core_double→DOUBLE`, `core_float→REAL`, `core_bool→BOOLEAN`.
6. **No path found** — throw `SemanticJoinException` naming the target
   table and every prior it tried.

## Usage

**From the CLI:**

```bash
echo "SELECT * FROM cities c JOIN countries USING PLACE" \
  | ./scripts/rewrite-sql.sh
```

**From the mesh driver's REST endpoint** — when `hitorro-mesh-datasets`
is on the driver-app's classpath (v3.0.1 default):

```bash
curl -X POST http://localhost:8085/mesh/queries -H 'Content-Type: application/json' -d '{
  "sql":      "SELECT * FROM wikidata_cities wd JOIN natural_earth_countries ne USING PLACE",
  "semantic": true
}'
```

The response includes `rewrittenSql` (the concrete SQL that ran), or
`null` if no rewrite was needed. Errors bubble as HTTP 400 with a message
you can read.

The driver also logs every rewrite at INFO level:

```
[semantic] rewrote USING PLACE
  in : SELECT wd.name FROM wikidata_cities wd JOIN natural_earth_countries ne USING PLACE LIMIT 5
  out: SELECT wd.name FROM wikidata_cities wd JOIN natural_earth_countries ne ON wd.country_iso = ne.iso_a2 LIMIT 5
```

**From Java** — Spring host apps get a `PlaceJoinRewriter` bean
auto-configured:

```java
@Autowired PlaceJoinRewriter rewriter;
String concreteSql = rewriter.rewrite(userSql);
```

Non-Spring hosts wire it manually:

```java
DatasetRegistry registry = new DatasetRegistry().loadBundled();
registry.scanInstalled();
PlaceJoinRewriter rewriter = new PlaceJoinRewriter(registry);
```

## Scope of the MVP

**Supported:**
- `USING PLACE` after `JOIN` (with or without an alias, with or without `AS`)
- `EXACT_ID` relationships declared in either direction
- `SPATIAL` relationships — see the section below for the MVP shape
- Chained JOINs (each can reference any prior table)
- Automatic `CAST` on type mismatch
- Two-pass resolution: precise EXACT_ID paths win over approximate
  SPATIAL paths whenever both exist between the same pair
- Rewritten SQL preserves the surrounding text verbatim

**Not yet:**
- `USING ENTITY` (probabilistic joins with `CONFIDENCE > x` filters)
- Multi-column composite keys (`via: [a, b]` for EXACT_ID) — throws with a clear message
- Multi-hop transitive resolution (A → C via B without explicitly joining B)
- Comments or string literals containing `USING PLACE` (regex-based
  detector doesn't tokenise). The jvssql planner extension in the ROADMAP
  is the correct fix — this MVP is a preprocessor that proves the model.

## SPATIAL — bounding-box MVP

> **State of the world (v3.0.1).** The rewriter produces correct standard
> SQL for SPATIAL joins today, but the mesh's phase-1 execution layer
> (jvssql) requires every join to carry at least one equijoin key —
> range-only and `CROSS JOIN` conditions get rejected with
> *"Phase 1 join requires at least one equijoin key"*. The rewriter's
> output is valid against any SQL engine that supports range joins;
> in-mesh execution unblocks when phase-4b (shuffle-hash-join, arbitrary
> conditions) lands, without any change to the rewriter or manifests.

A `SPATIAL` relationship in a manifest looks like:

```yaml
- target: natural-earth-countries
  kind: SPATIAL
  via: [latitude, longitude]      # source columns carrying the point
  params:
    predicate: within
    targetField: geometry
```

The rewriter emits a **`CROSS JOIN` with a bounding-box `WHERE` filter**
(not a true point-in-polygon):

```sql
JOIN natural_earth_countries ne USING PLACE

-- becomes

CROSS JOIN natural_earth_countries ne
WHERE e.latitude  BETWEEN ne.min_lat AND ne.max_lat
  AND e.longitude BETWEEN ne.min_lon AND ne.max_lon
```

Existing `WHERE` predicates are preserved (the bbox filter is `AND`-ed in
front of them). Chains with a mix of EXACT_ID + SPATIAL joins produce
`JOIN … ON …` for the EXACT_ID parts and `CROSS JOIN` + injected WHERE
for the SPATIAL parts.

For this to work the polygon dataset must carry four columns tagged with
roles `geo.bbox.min_lat`, `geo.bbox.max_lat`, `geo.bbox.min_lon`,
`geo.bbox.max_lon`. `natural-earth-countries`' install script computes
these from the polygon rings at install time.

### Known limitations of the MVP

- **Bounding box only.** A point inside a country's bounding rectangle
  but outside the actual polygon still matches. Fine for coarse
  country attribution; wrong for anything that needs true PIP.
- **Antimeridian.** Countries crossing 180° longitude (Fiji, Russia
  with the Chukchi Peninsula, USA with the Aleutian Islands) get
  bboxes spanning the full globe. Every earthquake worldwide will
  match Fiji.
- **Multi-part countries.** Kiribati's three island groups get one
  merged bounding rectangle covering half the Pacific.
- **No nearest / distance operators.** Only containment via BETWEEN.

Because the MVP produces approximate results, the rewriter prefers
`EXACT_ID` over `SPATIAL` whenever both paths exist. NOAA stations for
example have both a direct SPATIAL edge to Natural Earth (imprecise)
AND an indirect EXACT_ID path through `wikidata_countries` (precise via
FIPS→ISO). Explicitly listing `wikidata_countries` in the JOIN chain
opts into the precise path; leaving it out drops back to the bbox
approximation.

### Long-term fix

Real point-in-polygon needs a spatial index at the agent side — JTS
R-tree or S2 cells. That's a mesh-side change (agent grows an in-memory
spatial index over the polygon column) that the rewriter would then
emit `ST_Contains(...)` predicates for. Roadmap.

## Why a preprocessor, not a Calcite grammar extension

The mesh's SQL is already Calcite. Extending Calcite's grammar requires
either subclassing `SqlAbstractParserImpl` (a lot of ceremony for one
keyword) or forking Calcite's freemarker parser template (adds a build
step to a distant dependency).

A preprocessor:
- Ships today, in one Java class, with no build changes.
- Runs before the SQL ever hits Calcite, so if the rewriter is wrong you
  can bypass it and pass raw SQL through.
- Explains itself: the "before" and "after" are both plain SQL you can
  read.

The proper Calcite path is on the roadmap; this preprocessor is the way
the system uses declared relationships **today**.
