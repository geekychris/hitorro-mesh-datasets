#!/usr/bin/env bash
# US States, DC + territories — curated JSON that ships in the module.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="us-states"
SOURCE="$MODULE_ROOT/src/main/resources/data/us-states.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

cp "$SOURCE" "$RAW_DIR/us-states.json"
info "sha256: $(sha256_of "$RAW_DIR/us-states.json")"

info "json → ndjson (jq)"
jq -c '.[]' "$RAW_DIR/us-states.json" > "$DATA_DIR/states.ndjson"

finalize_ndjson "$DATA_DIR/states.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/us_states.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/us-states.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Every state that joined the Union before 1800
  SELECT name, capital, statehood_year FROM us_states
  WHERE statehood_year < 1800 ORDER BY statehood_year;

  # Total population per US Census region (2020)
  SELECT region, SUM(population_2020) AS pop, COUNT(*) AS n_states
  FROM us_states WHERE region IS NOT NULL
  GROUP BY region ORDER BY pop DESC;

  # US cities enriched with state region + division
  SELECT c.name AS city, s.name AS state, s.region, c.population
  FROM geonames_cities15000 c
  JOIN us_states s ON c.admin1_code = s.postal
  WHERE c.country_code = 'US'
  ORDER BY c.population DESC LIMIT 15;

EOF
