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
| **Wikidata — item + labels** | Planned | v3.2 | CC0. The identity glue. The full dump is huge; MVP filters to Q-items with a GeoNames or ISO cross-reference. Enables `USING ENTITY` joins. |
| **US Census / ACS — 5-year estimates** | Planned | v3.2 | Public domain (federal government work). API-key required — install script prompts for one. Joins to GeoNames via FIPS. |
| **NOAA GHCN-Daily — climate normals** | Planned | v3.3 | Public domain. Station lat/lon enables spatial joins to any polygon dataset. |
| **Our World in Data — economics + health** | Planned | v3.3 | Usually CC-BY. Long-form time series that joins to country info. |

## Additional datasets, in order of return-on-effort

| Collection | State | When | Notes |
|------------|-------|------|-------|
| **OpenAlex — works + authors** | Planned | v3.4 | CC0. Enables the "papers about X, filtered by institution country" kind of query. |
| **Crossref — DOI metadata** | Planned | v3.4 | Mostly free reuse. Bibliographic backbone; abstracts need per-record caution. |
| **Overture Maps — places + buildings** | Planned | v3.5 | CDLA-permissive for their own layers, ODbL for OSM-derived layers — `LicenseAlgebra` should surface the mixed obligation cleanly. |
| **OpenStreetMap — POI + roads** | Planned | v3.5 | ODbL. Kept deliberately separate from any CC-* dataset so share-alike inheritance is visible in queries. |
| **World Bank — country indicators** | Planned | v3.6 | CC-BY 4.0. Overlaps OWID; both worth having. |
| **USGS — earthquakes + elevation + water** | Planned | v3.6 | Public domain. Time-series + geospatial. |
| **Election / campaign finance** | Planned | later | State-level; licences vary. |
| **Transportation networks** | Planned | later | GTFS feeds for major cities. |

## Engine features these datasets need

Ordered by which datasets unlock them:

| Feature | State | Unlocks | Notes |
|---------|-------|---------|-------|
| Runtime table registration API | Shipped | Everything | Already in the driver (phase 7p). This module talks to it. |
| Broadcast dimension tables | Shipped | Any small lookup | Country info uses it today. |
| `LicenseAlgebra.combine` | Shipped | Query-time license warnings | Registered but not yet consulted by the planner. |
| **Bundled manifest → auto-registration** | Planned | UX polish | A Spring Boot module that reads bundled manifests and auto-registers any dataset it finds installed. |
| **`USING PLACE` SQL syntax** | Planned | GeoNames ↔ Census ↔ NOAA | Needs jvssql planner extension. Resolves to an EXACT_ID or HIERARCHICAL join walk. |
| **`USING ENTITY` SQL syntax** | Planned | Wikidata-glue joins | Same idea, but through the Wikidata cross-reference graph. |
| **`CONFIDENCE > x` join filter** | Planned | Probabilistic joins | The confidence score already fits on a `RecordEnvelope`; needs SQL surface + planner rule. |
| **Spatial predicate joins** | Planned | Any point-vs-polygon query | Needs a spatial index on the agent side — either JTS with an R-tree or S2 cells. |
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
