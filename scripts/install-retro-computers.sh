#!/usr/bin/env bash
# Retro computers — curated JSON that ships in the module resources.
# No external API; just materialise the shipped data.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="retro-computers"
SOURCE="$MODULE_ROOT/src/main/resources/data/retro-computers.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

cp "$SOURCE" "$RAW_DIR/retro-computers.json"
info "sha256: $(sha256_of "$RAW_DIR/retro-computers.json")"

info "json → ndjson (jq)"
jq -c '.[]' "$RAW_DIR/retro-computers.json" > "$DATA_DIR/computers.ndjson"

finalize_ndjson "$DATA_DIR/computers.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/retro_computers.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/retro-computers.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # The 8-bit era at a glance
  SELECT name, manufacturer, release_year, cpu, ram_kb, launch_price_usd
  FROM retro_computers WHERE release_year BETWEEN 1980 AND 1985
  ORDER BY release_year, launch_price_usd;

  # Cheapest home computers ever
  SELECT name, manufacturer, release_year, launch_price_usd, ram_kb
  FROM retro_computers WHERE form_factor = 'home computer'
  ORDER BY launch_price_usd ASC LIMIT 15;

  # RAM per dollar over time
  SELECT release_year, name, ram_kb, launch_price_usd,
         (ram_kb * 1.0 / launch_price_usd) AS kb_per_usd
  FROM retro_computers WHERE launch_price_usd IS NOT NULL
  ORDER BY release_year;

  # Manufacturer distribution
  SELECT manufacturer, COUNT(*) AS n_models
  FROM retro_computers GROUP BY manufacturer ORDER BY n_models DESC;

EOF
