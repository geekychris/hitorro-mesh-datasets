#!/usr/bin/env bash
# GeoNames cities > 15 000 population.
# Idempotent: re-running just re-verifies. Set HITORRO_DATASETS_FORCE=1 to
# force a re-download.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="geonames-cities15000"
URL="https://download.geonames.org/export/dump/cities15000.zip"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

# ---- download ----
fetch "$URL" "$RAW_DIR/cities15000.zip"
info "sha256: $(sha256_of "$RAW_DIR/cities15000.zip")"

# ---- unzip ----
require_cmd unzip
info "unzip"
unzip -qo "$RAW_DIR/cities15000.zip" -d "$RAW_DIR"

# ---- TSV → NDJSON ----
COLS="geonameid,name,asciiname,alternatenames,latitude,longitude,feature_class,feature_code,country_code,cc2,admin1_code,admin2_code,admin3_code,admin4_code,population,elevation,dem,timezone,modification_date"
# Numeric fields per the JVS type. admin3/4/dem/modification aren't in the
# type — they'll be dropped by the schema when the mesh reads the NDJSON,
# but we emit them so the raw form is faithful.
NUMERIC="geonameid,latitude,longitude,population,elevation,dem"

tsv_to_ndjson "$RAW_DIR/cities15000.txt" "$DATA_DIR/cities15000.ndjson" "$COLS" "$NUMERIC"

# ---- ship the JVS type + manifest into the install dir ----
cp "$MODULE_ROOT/src/main/resources/types/geonames_cities15000.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/geonames-cities15000.yaml" "$INSTALL_DIR/manifest.yaml"

# ---- register-with-mesh hint ----
cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/cities15000.zip
    raw/cities15000.txt
    data/cities15000.ndjson
    types/geonames_cities15000.json

To make it a queryable mesh table:

  1. Configure an agent to load it — add to its yaml:

       hitorro:
         mesh:
           agent:
             capabilities: [jvssql, partition:geonames_cities15000:all]
             tables:
               - name: geonames_cities15000
                 partition-key: all
                 type-json-resource: file:$TYPES_DIR/geonames_cities15000.json
                 ndjson-file: file:$DATA_DIR/cities15000.ndjson

  2. Announce it to a running driver:

       ./scripts/register-with-mesh.sh geonames-cities15000

  3. Query:

       SELECT country_code, COUNT(*) AS n, AVG(population) AS avg_pop
       FROM geonames_cities15000
       GROUP BY country_code
       ORDER BY n DESC
       LIMIT 10;

EOF
