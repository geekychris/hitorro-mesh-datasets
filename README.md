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
| **Registry** (`com.hitorro.mesh.datasets.registry`) | In-memory catalog (`DatasetRegistry`) + a REST client (`MeshRegistrar`) that announces installed datasets to a running mesh driver. |
| **Install scripts** (`scripts/`) | Bash + awk downloaders that materialise the raw source into NDJSON + a JVS type file under `$HITORRO_DATASETS_HOME`. No Python. |
| **JVS types** (`src/main/resources/types/`) | The schema files the mesh agent-app loads to know how to read the NDJSON. |

## Datasets shipped in v3.0.1

Small, correct, and enough to prove the model end-to-end:

| id | title | rows | licence | role |
|----|-------|-----:|---------|------|
| `geonames-cities15000` | GeoNames — cities > 15 000 population | ~26 000 | CC-BY-4.0 | Distributed table |
| `geonames-country-info` | GeoNames — country info | ~250 | CC-BY-4.0 | Broadcast dimension |

Everything else in the design (Wikidata, Natural Earth, OSM/Overture, Census,
NOAA, Our World in Data, OpenAlex, Crossref) is planned — see [`ROADMAP.md`](ROADMAP.md).

## Quick start

```bash
# 1. install both datasets (about 5 seconds; downloads ~3 MB from geonames.org)
./scripts/install-all.sh

# 2. start a mesh (see hitorro-mesh-examples/scripts/mesh-up.sh)

# 3. configure agents to load the data — see the note the install script prints,
#    or just add these lines to your agent yaml under hitorro.mesh.agent.

# 4. tell the driver
./scripts/register-with-mesh.sh geonames-country-info --broadcast
./scripts/register-with-mesh.sh geonames-cities15000

# 5. query
curl -s http://localhost:8085/queries -H 'Content-Type: application/json' -d '{
  "sql": "SELECT c.name, c.population, ci.continent, ci.languages
          FROM geonames_cities15000 c
          JOIN geonames_country_info ci ON c.country_code = ci.iso
          WHERE c.population > 500000
          ORDER BY c.population DESC LIMIT 20"
}'
```

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
