# hitorro-mesh-datasets — roadmap

The initial release ships the plumbing plus two working datasets. Everything
below is planned. Each entry is roughly one iteration's worth of work.

**Legend:** *Shipped* = usable today · *Planned* = designed, not built · *Out of scope* = not this module.

## The catalog — seven-collection MVP

| Collection | State | Since | Notes |
|------------|-------|-------|-------|
| **GeoNames — cities >15k** | Shipped | v3.0.1 | ~26 000 rows, CC-BY. Distributed. |
| **GeoNames — country info** | Shipped | v3.0.1 | ~250 rows, CC-BY. Broadcast. |
| **GeoNames — alternate names v2** | Planned | v3.1 | The multilingual layer. Enormous; needs partitioning by geoname id. |
| **Natural Earth — countries (1:110m)** | Shipped | v3.0.1 | Public domain. GeoJSON; polygon geometries preserved in the NDJSON. |
| **Natural Earth — admin1 (states/provinces)** | Planned | v3.1 | Same source pattern as countries — one level down. |
| **Natural Earth — rivers, lakes, coastlines** | Planned | v3.2 | Physical-geography layer; unlocks distance-to-coastline queries. |
| **Wikidata — cities > 100k** | Shipped | v3.0.1 | CC0. Identity glue seed: ~2 200 rows via SPARQL, 98 % carry GeoNames IDs and 99 % carry ISO country codes. First dataset that speaks the `wikidata` namespace — three other manifests already map to it. |
| **Wikidata — countries** | Shipped | v3.0.1 | CC0. ~217 rows via SPARQL — every sovereign state carrying ISO-2/ISO-3/ISO-numeric/FIPS/M.49 identifiers plus continent + population. Made NOAA → Natural Earth possible in one semantic-join hop (previously needed geonames-country-info in the middle). |
| **Wikidata — city sitelinks bridge** | Shipped | v3.0.1 | CC0. ~1500 rows: city QID → enwiki article slug. Turned wikipedia-pageviews into a queryable geographic layer through joined semantic queries. |
| **Wikidata — multilingual labels** | Planned | v3.2 | CC0. Extends the cities dataset with `name.<lang>` per BCP-47 language tag. Uses the existing JVS multilingual field machinery. |
| **Wikidata — full items with a GeoNames cross-ref** | Planned | v3.3 | CC0. Broader net: every Q-item that has P1566, not just cities. Millions of rows; needs partition-by-first-QID-char. |
| **US Census / ACS — 5-year estimates** | Planned | v3.2 | Public domain (federal government work). API-key required — install script prompts for one. Joins to GeoNames via FIPS. |
| **NOAA GHCN-Daily — station inventory** | Shipped | v3.0.1 | Public domain. ~132 500 stations worldwide with lat/lon/elevation + derived fips_country that joins to geonames-country-info via `id.fips` role match. Declares SPATIAL to Natural Earth (activates once the rewriter learns SPATIAL). |
| **NOAA GHCN-Daily — daily observations** | Planned | v3.1 | Public domain. Terabytes of daily min/max temp, precip, wind per station. Needs partitioning by station-id prefix — first properly distributed (not broadcast) large dataset. |
| **NOAA — climate normals (30-year)** | Planned | v3.2 | Public domain. Per-station monthly / annual normals — small enough to broadcast, gives the "average July temperature in Palermo" flavor of query. |
| **Our World in Data — economics + health** | Planned | v3.3 | Usually CC-BY. Long-form time series that joins to country info. Complements World Bank (shipped v3.0.1) with topic-focused normalised datasets. |

## Additional datasets, in order of return-on-effort

| Collection | State | When | Notes |
|------------|-------|------|-------|
| **OpenAlex — top institutions** | Shipped | v3.0.1 | CC0. ~400 rows sorted by citation count. First scholarly-research dataset. Joins to any country dataset via ISO-2. |
| **OpenAlex — works + authors** | Planned | v3.4 | CC0. Extends the shipped institutions dataset. Enables the "papers about X, filtered by institution country" kind of query. Millions of works — needs partitioning. |
| **Crossref — DOI metadata** | Planned | v3.4 | Mostly free reuse. Bibliographic backbone; abstracts need per-record caution. |
| **Overture Maps — places + buildings** | Planned | v3.5 | CDLA-permissive for their own layers, ODbL for OSM-derived layers — `LicenseAlgebra` should surface the mixed obligation cleanly. |
| **OpenStreetMap — airports (via Overpass)** | Shipped | v3.0.1 | ODbL. ~1 400 rows via Overpass API. First share-alike dataset — the UI now shows an ODbL warning callout on the Datasets tab explaining the obligation. |
| **OpenStreetMap — full POI + roads** | Planned | v3.5 | ODbL. Broader Overpass queries or bulk PBF ingestion via a spatial extract step. |
| **World Bank — country indicators (snapshot)** | Shipped | v3.0.1 | CC-BY. 213 countries × 8 latest-year indicators (GDP, per-capita GDP, population, life expectancy, urban %, unemployment %, electricity access %). Joins via ISO alpha-3. |
| **World Bank — indicators time-series** | Planned | v3.2 | Same source, but the full 30-year history per (country, indicator). Second dataset in the catalog needing partition-by-something-not-country. |
| **USGS — earthquakes (last-month feed)** | Shipped | v3.0.1 | Public domain. ~11 000 events with point geometry + magnitude + alert level. Refreshable daily. |
| **USGS — historical earthquakes + water + elevation** | Planned | v3.6 | Public domain. Long-form time series bulk downloads. |
| **Election / campaign finance** | Planned | later | State-level; licences vary. |
| **Transportation networks** | Planned | later | GTFS feeds for major cities. |

## Engine features these datasets need

Ordered by which datasets unlock them:

| Feature | State | Unlocks | Notes |
|---------|-------|---------|-------|
| Runtime table registration API | Shipped | Everything | Already in the driver (phase 7p). This module talks to it. |
| Broadcast dimension tables | Shipped | Any small lookup | Country info uses it today. |
| `LicenseAlgebra.combine` | Shipped | Query-time license warnings | Registered but not yet consulted by the planner. |
| **Bundled manifest → auto-registration** | Shipped | v3.0.1 | `Autoregistrar` + Spring Boot autoconfigure + CLI + `./scripts/register-installed.sh`. Zero-friction auto-registration on driver-app startup when the datasets jar is on the classpath. |
| **`USING PLACE` SQL syntax** | Shipped (preprocessor) | v3.0.1 | `PlaceJoinRewriter` rewrites `JOIN t USING PLACE` → `JOIN t ON a.x = b.y` using declared `EXACT_ID` relationships, with automatic `CAST` on type mismatch and role-based target-column selection (`country_iso → iso_a2` not `iso_a3`). Wired into the mesh-driver-app's `/mesh/queries` endpoint behind a `semantic:true` request flag; auto-registered dataset manifests. Regex-based; a jvssql-native Calcite grammar extension is the future upgrade. |
| **`USING ENTITY` SQL syntax** | Planned | Wikidata-glue joins | Same idea, but through the Wikidata cross-reference graph. |
| **`CONFIDENCE > x` join filter** | Planned | Probabilistic joins | The confidence score already fits on a `RecordEnvelope`; needs SQL surface + planner rule. |
| **Spatial predicate joins — bbox rewriter** | Shipped | v3.0.1 | Rewriter emits `CROSS JOIN + WHERE bbox` for SPATIAL relationships. Polygon datasets carry `geo.bbox.{min,max}_{lat,lon}` role-tagged columns computed at install time. Output is valid SQL against any range-join-capable engine. Approximate — antimeridian and multi-part countries have known false positives. |
| **Spatial join execution in-mesh** | Blocked | after v3.1 | The bbox-rewriter output can't be dispatched by phase-1 jvssql (requires at least one equijoin key). Unblocks the moment shuffle-hash-join (phase 4b) lands and arbitrary join conditions become dispatchable. No rewriter or manifest change needed at that point — SPATIAL starts executing automatically. |
| **Spatial predicate joins — real PIP** | Planned | v3.4 | Point-in-polygon via a spatial index at the agent (JTS R-tree or S2 cells). Rewriter would emit `ST_Contains(polygon, POINT(lon, lat))`. |
| **Overture / OSM ingest** | Planned | Detailed maps | Blocker: no pure-Java Overture/OSM PBF reader is in the mesh today. |
| **Wikidata TTL/JSON ingest** | Planned | Identity glue | Blocker: the full dump is ~130 GB. Filtered subset is realistic first pass. |
| **License-aware result marker** | Planned | Redistribution safety | Every query result should carry the combined license capabilities in its response envelope. |

## Non-goals

- Not a replacement for a warehouse. If you want to load a fixed set of tables
  once and query them forever, use one. This module optimises for *discovering*
  a dataset, mounting it against a mesh, and being able to *throw it out*
  cleanly.
- Not a data-quality product. If a source is dirty, we surface it verbatim
  under `native` and let the user decide what to trust.
- Not an entity-resolution product. Probabilistic joins are supported as a
  first-class citizen but the actual matching heuristics live in whichever
  dataset chooses to publish them.
