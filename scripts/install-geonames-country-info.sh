#!/usr/bin/env bash
# GeoNames countryInfo.txt — one row per country. Tiny (about 250 rows,
# 32 KB) so it makes a natural broadcast table on every jvssql agent.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="geonames-country-info"
URL="https://download.geonames.org/export/dump/countryInfo.txt"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

fetch "$URL" "$RAW_DIR/countryInfo.txt"
info "sha256: $(sha256_of "$RAW_DIR/countryInfo.txt")"

# countryInfo.txt starts with several '#' comment lines including a schema
# header — the awk in common.sh skips them.
COLS="iso,iso3,iso_numeric,fips,country,capital,area_sq_km,population,continent,tld,currency_code,currency_name,phone,postal_code_format,postal_code_regex,languages,geonameid,neighbours,equivalent_fips"
NUMERIC="area_sq_km,population,geonameid"

tsv_to_ndjson "$RAW_DIR/countryInfo.txt" "$DATA_DIR/country_info.ndjson" "$COLS" "$NUMERIC"

cp "$MODULE_ROOT/src/main/resources/types/geonames_country_info.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/geonames-country-info.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/countryInfo.txt
    data/country_info.ndjson
    types/geonames_country_info.json

This one is small enough to broadcast to every jvssql agent. Add to each
agent's yaml under 'broadcastTables':

  hitorro:
    mesh:
      agent:
        broadcast-tables:
          - name: geonames_country_info
            type-json-resource: file:$TYPES_DIR/geonames_country_info.json
            ndjson-file: file:$DATA_DIR/country_info.ndjson

Then announce to the driver:

  ./scripts/register-with-mesh.sh geonames-country-info --broadcast

Now this join works — every city with its country's continent and language:

  SELECT c.name, c.population, ci.country, ci.continent, ci.languages
  FROM geonames_cities15000 c
  JOIN geonames_country_info ci ON c.country_code = ci.iso
  WHERE c.population > 500000
  ORDER BY c.population DESC
  LIMIT 20;

EOF
