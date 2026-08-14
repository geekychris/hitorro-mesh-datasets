# hitorro-mesh-datasets

**A catalog of clonable, queryable public datasets** for Hitorro Mesh.

The idea: a developer should be able to discover a dataset, install it with one
script, and have it show up as a first-class table in the mesh — joinable to
every other dataset that shares an identifier namespace with it.

This is the YQL-2.0 seed layer. YQL was "SQL over the web"; this is *"SQL over
a discoverable universe of semantically connected public datasets"* — using the
mesh's Calcite planner, distributed execution, and streaming iterator model as
the substrate.

## What's in this module

| Piece | What it does |
|-------|--------------|
| **Manifests** (`src/main/resources/manifests/*.yaml`) | One YAML per dataset. Declares licence, source URL, record schema, identifier namespaces, partitioning strategy, and cross-dataset relationships. |
| **Model** (`com.hitorro.mesh.datasets.model`) | `Manifest`, `License`, `Source`, `RecordSpec`, `Relationship`, `RelationshipKind`, `IdentitySpec` — the publishable descriptors. |
| **Envelope** (`com.hitorro.mesh.datasets.envelope.RecordEnvelope`) | The common `@id` / `@type` / `names` / `links` wrapper every normalised record wears. Native record preserved verbatim. |
| **Loader** (`com.hitorro.mesh.datasets.loader`) | YAML → `Manifest` parser, plus a `LicenseAlgebra` that answers "can I combine A + B and redistribute the result?" |
| **Registry** (`com.hitorro.mesh.datasets.registry`) | In-memory catalog (`DatasetRegistry`) + REST client (`MeshRegistrar`) + one-shot orchestrator (`Autoregistrar`) that scans installed datasets and posts each to a running driver. |
| **Spring autoconfig** (`com.hitorro.mesh.datasets.spring`) | Optional Spring Boot integration: adds `hitorro.mesh.datasets.*` properties + an `ApplicationRunner` that auto-registers on startup. Spring is `<optional>true</optional>` — non-Spring consumers pay nothing. |
| **Semantic joins** (`com.hitorro.mesh.datasets.semantic`) | `PlaceJoinRewriter` — turns `JOIN t USING PLACE` into a concrete `ON a.x = b.y` clause by walking the declared relationships. Handles type-mismatch with `CAST`, forward + reverse declaration lookup, multi-hop chains. See [`docs/SEMANTIC_JOINS.md`](docs/SEMANTIC_JOINS.md). |
| **CLI** (`com.hitorro.mesh.datasets.cli`) | `RegisterInstalledCli` + `RewriteSqlCli`, each wrapped by a `scripts/*.sh` for manual / cron invocation. |
| **Install scripts** (`scripts/`) | Bash + awk (+ jq for GeoJSON) downloaders that materialise the raw source into NDJSON + a JVS type file under `$HITORRO_DATASETS_HOME`. No Python. |
| **JVS types** (`src/main/resources/types/`) | The schema files the mesh agent-app loads to know how to read the NDJSON. |

## Datasets shipped in v3.0.1

Small, correct, and enough to prove the model end-to-end across three licence
classes — with Wikidata joining GeoNames and Natural Earth through QIDs and
cross-referenced ISO / GeoNames identifiers:

| id | title | rows | licence | role |
|----|-------|-----:|---------|------|
| `geonames-cities15000` | GeoNames — cities > 15 000 population | ~26 000 | CC-BY-4.0 | Distributed table |
| `geonames-country-info` | GeoNames — country info | ~250 | CC-BY-4.0 | Broadcast dimension |
| `natural-earth-countries` | Natural Earth — admin-0 countries (1:110m) | ~258 | Public domain | Broadcast dimension (+ polygon geometry) |
| `wikidata-cities` | Wikidata — cities > 100 000 with cross-references | ~2 200 | CC0 | Identity glue (broadcast, produces the `wikidata` namespace) |
| `noaa-ghcnd-stations` | NOAA — GHCN-Daily station inventory | ~132 500 | Public domain | Point geometry (broadcast, derived `fips_country` joins to country info; declares SPATIAL to Natural Earth for the eventual spatial-join iteration) |
| `wikidata-countries` | Wikidata — countries with full identifier cross-references | ~217 | CC0 | The country identifier hub — one row per Wikidata QID carrying ISO-2/ISO-3/ISO-numeric/FIPS/M.49 so any dataset speaking any country id can hop through it |
| `worldbank-indicators` | World Bank — country socioeconomic indicators (snapshot) | ~213 | CC-BY | GDP, GDP/capita, population, life expectancy, urban %, unemployment %, electricity access %. Latest year per country. First socioeconomic dataset. |
| `usgs-earthquakes` | USGS — earthquakes (last 30 days) | ~11 000 | Public domain | Every seismic event in the last month, with mag/lat/lon/depth/alert. Refreshable daily via `HITORRO_DATASETS_FORCE=1`. First event stream. |
| `owid-co2-latest` | Our World in Data — CO2 & energy (latest year) | ~215 | CC-BY | Total/per-capita CO2, coal/oil/gas breakdown, primary energy, share-of-global. Joins to worldbank/wikidata/natural-earth via ISO-3. |
| `wikipedia-pageviews` | Wikipedia — top-1000 pageviews (English, latest day) | 1 000 | CC-BY-SA | Yesterday's most-viewed articles. Refreshable daily. First zeitgeist dataset. |
| `osm-airports` | OpenStreetMap — airports with IATA codes | ~1 400 | ODbL | First ODbL (share-alike as a database) dataset — combining with any other source triggers the LicenseAlgebra's share-alike flag. Sourced via Overpass API. |
| `wikidata-city-sitelinks` | Wikidata — city → enwiki article-slug bridge | ~1 500 | CC0 | Bridge dataset: connects `wikidata_cities` (city entity) to `wikipedia_pageviews` (article slug). Turns "yesterday's top-viewed Wikipedia articles" into a queryable geographic layer. |
| `openalex-institutions` | OpenAlex — top research institutions | ~400 | CC0 | Universities, national labs, hospitals, industry R&D — sorted by citation count. First scholarly-research dataset. Joins via ISO-2 country code. |
| `coingecko-crypto` | CoinGecko — top-100 cryptocurrencies by market cap | 100 | Custom (attribution) | First financial dataset. Live price, market cap, 24h change, supply, all-time high. Refreshable on demand. |

Everything else in the design (Wikidata, Natural Earth, OSM/Overture, Census,
NOAA, Our World in Data, OpenAlex, Crossref) is planned — see [`ROADMAP.md`](ROADMAP.md).

## Quick start

```bash
# 1. install every shipped dataset (~5 seconds; downloads ~4 MB total)
./scripts/install-all.sh

# 2. start a mesh (see hitorro-mesh-examples/scripts/mesh-up.sh)

# 3. configure agents to load the NDJSON — see the note each install
#    script prints for the exact yaml block.

# 4. tell the driver about everything you've installed, in one shot:
./scripts/register-installed.sh

# 5. query
curl -s http://localhost:8085/queries -H 'Content-Type: application/json' -d '{
  "sql": "SELECT c.name, c.population, ci.continent, ci.languages
          FROM geonames_cities15000 c
          JOIN geonames_country_info ci ON c.country_code = ci.iso
          WHERE c.population > 500000
          ORDER BY c.population DESC LIMIT 20"
}'
```

### Semantic joins — `USING PLACE`

The relationships each manifest declares aren't documentation. Write:

```sql
SELECT gn.name, ci.continent, ne.income_grp
FROM geonames_cities15000 gn
JOIN geonames_country_info  ci USING PLACE
JOIN natural_earth_countries ne USING PLACE
WHERE gn.population > 500000
```

and get one of three paths:

**1. Preview locally via the CLI:**
```bash
./scripts/rewrite-sql.sh < query.sql
```

**2. Live on the driver** (when this module is on the mesh-driver-app's
classpath, which is now the default in `hitorro-mesh-driver-app` v3.0.1):
```bash
curl -X POST http://localhost:8085/mesh/queries -H 'Content-Type: application/json' -d '{
  "sql":     "SELECT wd.name FROM wikidata_cities wd JOIN natural_earth_countries ne USING PLACE LIMIT 5",
  "semantic": true
}'
```
The response's `rewrittenSql` field carries the concrete SQL the planner
actually saw; unchanged queries leave it `null`.

**3. From Java** — inject `PlaceJoinRewriter` (auto-configured) and rewrite
before whatever executor you're using.

Full mechanism, including role-based target-column selection and CAST
insertion, in [`docs/SEMANTIC_JOINS.md`](docs/SEMANTIC_JOINS.md).

### Auto-registration for Spring Boot drivers

If your mesh-driver-app has `hitorro-mesh-datasets` on its classpath,
registration happens on startup — no script needed:

```yaml
# application.yml
hitorro:
  mesh:
    datasets:
      auto-register: true          # default
      driver-url: http://localhost:8085
      # skip: [docs]               # ids to leave alone
      # fail-on-error: false       # true → boot fails on any registration error
```

The autoconfig kicks in only when Spring is on the classpath; non-Spring
consumers pay nothing. Under the hood it's the same `Autoregistrar` class
the CLI wraps.

## The design in one page

### Manifest = the thing people publish

Every dataset is described by one YAML file. That file is enough for the
system to:

1. **Install** the raw source (URL, checksum, format).
2. **Read** it into typed records (`RecordSpec` → JVS type file).
3. **Register** it with a mesh driver as a distributed or broadcast table.
4. **Reason** about joins (`identifiers` → namespaces this dataset speaks;
   `relationships` → declared joins to other datasets).
5. **Know** what's legal to redistribute (`license` → capabilities, not a bare SPDX string).

### Three data layers

```
RAW         source-faithful records — never rewritten
NORMALIZED  common semantic fields + canonical identifiers via RecordEnvelope
DERIVED     joins, enrichments, aggregates, spatial relationships — lazy
```

RAW and NORMALIZED are persisted and versioned. DERIVED is produced through the
mesh's streaming runtime and materialised only when it's worth caching.

### Four kinds of join

```
EXACT_ID       same identifier value in the same namespace
HIERARCHICAL   parent/child in a well-known containment tree
SPATIAL        point-in-polygon, nearest, intersects, distance-lt
PROBABILISTIC  entity resolution with a confidence score + evidence
```

The planner picks the physical strategy per kind. Probabilistic joins never
masquerade as truth — every matched row carries `confidence` and `evidence`,
and SQL can filter on it (`USING ENTITY CONFIDENCE > .95`, on the roadmap).

### License algebra, not license strings

`License` records the five capabilities that actually matter — commercial use,
redistribution, attribution, share-alike, modification. `LicenseAlgebra.combine`
computes the terms of a joined result and warns when share-alike kicks in. This
means the driver can answer *"can this query's result be redistributed, and how?"*
before it runs.

## How to add a new dataset

1. Write `src/main/resources/manifests/<your-id>.yaml`.
2. Write `src/main/resources/types/<your_table_name>.json` (a JVS type — kebab-case id, snake-case table name).
3. Add the id to `ManifestLoader.BUNDLED` so the registry picks it up.
4. Write `scripts/install-<your-id>.sh` that downloads, converts to NDJSON,
   and copies the manifest + type into `$HITORRO_DATASETS_HOME/<your-id>/`.
   Use `common.sh` helpers.
5. Add tests to `ManifestLoaderTest` asserting the manifest parses cleanly.

The rest is automatic: the mesh registrar, the license algebra, and the
identifier-based lookup all work off the manifest.

## Layout

```
hitorro-mesh-datasets/
├── src/main/java/com/hitorro/mesh/datasets/
│   ├── model/         — Manifest, License, Source, Relationship, ...
│   ├── envelope/      — RecordEnvelope (common wrapper)
│   ├── loader/        — ManifestLoader, LicenseAlgebra
│   └── registry/      — DatasetRegistry, MeshRegistrar
├── src/main/resources/
│   ├── manifests/     — YAML per dataset
│   └── types/         — JVS type JSON per dataset
├── scripts/
│   ├── common.sh                        — download + TSV→NDJSON helpers
│   ├── install-<dataset-id>.sh          — one per dataset
│   ├── install-all.sh                   — install everything
│   └── register-with-mesh.sh <id>       — POST /mesh/tables
├── docs/
│   ├── DESIGN.md                        — the vision, at length
│   ├── LICENSE_ALGEBRA.md               — combination rules
│   └── CATALOG.md                       — shipped datasets + planned
├── pom.xml
├── ROADMAP.md
└── README.md
```

## Runtime layout

```
$HITORRO_DATASETS_HOME/          — default ~/.hitorro/datasets
└── <dataset-id>/
    ├── manifest.yaml
    ├── raw/       original download, untouched
    ├── data/      NDJSON partitions
    └── types/     JVS type JSON
```
