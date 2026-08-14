#!/usr/bin/env bash
# Natural Earth admin-0 countries (1:110m scale, GeoJSON).
# Ships as a FeatureCollection; we expand to one NDJSON line per Feature
# with the properties flattened to top-level keys plus the geometry preserved
# as a nested GeoJSON object. Requires jq (brew install jq / apt install jq).
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="natural-earth-countries"
URL="https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

fetch "$URL" "$RAW_DIR/ne_110m_admin_0_countries.geojson"
info "sha256: $(sha256_of "$RAW_DIR/ne_110m_admin_0_countries.geojson")"

# ---- GeoJSON → NDJSON ----
# Natural Earth property keys are UPPER_SNAKE_CASE. Rename to lower_snake_case
# to match the JVS type. Use `// null` guards so absent keys become explicit
# nulls rather than missing (matches TSV loader behaviour in common.sh).
info "geojson → ndjson (jq)"
jq -c '.features[] | {
    name:       (.properties.NAME       // null),
    name_long:  (.properties.NAME_LONG  // null),
    iso_a2:     (.properties.ISO_A2     // null),
    iso_a3:     (.properties.ISO_A3     // null),
    iso_n3:     (.properties.ISO_N3     // null),
    un_a3:      (.properties.UN_A3      // null),
    wb_a2:      (.properties.WB_A2      // null),
    wb_a3:      (.properties.WB_A3      // null),
    continent:  (.properties.CONTINENT  // null),
    subregion:  (.properties.SUBREGION  // null),
    region_un:  (.properties.REGION_UN  // null),
    region_wb:  (.properties.REGION_WB  // null),
    pop_est:    (.properties.POP_EST    // null),
    gdp_md:     (.properties.GDP_MD     // null),
    economy:    (.properties.ECONOMY    // null),
    income_grp: (.properties.INCOME_GRP // null),
    geometry:    .geometry
  }' "$RAW_DIR/ne_110m_admin_0_countries.geojson" > "$DATA_DIR/countries.ndjson"
ok "wrote $(wc -l < "$DATA_DIR/countries.ndjson") records to $DATA_DIR/countries.ndjson"

cp "$MODULE_ROOT/src/main/resources/types/natural_earth_countries.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/natural-earth-countries.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/ne_110m_admin_0_countries.geojson
    data/countries.ndjson
    types/natural_earth_countries.json

Small enough to broadcast. Add to each jvssql agent's yaml:

  hitorro:
    mesh:
      agent:
        broadcast-tables:
          - name: natural_earth_countries
            type-json-resource: file:$TYPES_DIR/natural_earth_countries.json
            ndjson-file: file:$DATA_DIR/countries.ndjson

Announce to the driver:

  ./scripts/register-with-mesh.sh natural-earth-countries --broadcast

Three-way join demo — cities > 1M with their Natural Earth country + GeoNames
country_info side-by-side, so you can spot discrepancies:

  SELECT c.name AS city, c.population,
         ne.name AS ne_country, ne.continent, ne.income_grp,
         gn.country AS gn_country, gn.languages
  FROM geonames_cities15000 c
  JOIN geonames_country_info ci ON c.country_code = ci.iso
  JOIN natural_earth_countries ne ON ci.iso = ne.iso_a2
  JOIN geonames_country_info gn ON c.country_code = gn.iso
  WHERE c.population > 1000000
  ORDER BY c.population DESC LIMIT 20;

EOF
