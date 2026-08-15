#!/usr/bin/env bash
# GeoNames cities > 500 population. ~180 000 places.
# ~15MB download. Same TSV schema as cities15000.
# Set HITORRO_DATASETS_FORCE=1 to re-download.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="geonames-cities500"
URL="https://download.geonames.org/export/dump/cities500.zip"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

fetch "$URL" "$RAW_DIR/cities500.zip"
info "sha256: $(sha256_of "$RAW_DIR/cities500.zip")"

require_cmd unzip
info "unzip"
unzip -qo "$RAW_DIR/cities500.zip" -d "$RAW_DIR"

COLS="geonameid,name,asciiname,alternatenames,latitude,longitude,feature_class,feature_code,country_code,cc2,admin1_code,admin2_code,admin3_code,admin4_code,population,elevation,dem,timezone,modification_date"
NUMERIC="geonameid,latitude,longitude,population,elevation,dem"

tsv_to_ndjson "$RAW_DIR/cities500.txt" "$DATA_DIR/cities500.ndjson" "$COLS" "$NUMERIC"

cp "$MODULE_ROOT/src/main/resources/types/geonames_cities500.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/geonames-cities500.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  SELECT country_code, COUNT(*) AS n, AVG(population) AS avg_pop
  FROM geonames_cities500 WHERE population > 0
  GROUP BY country_code ORDER BY n DESC LIMIT 10;

EOF
