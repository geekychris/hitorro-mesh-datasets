#!/usr/bin/env bash
# UN M49 area codes — curated JSON that ships in the module resources.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="un-m49-areas"
SOURCE="$MODULE_ROOT/src/main/resources/data/un-m49-areas.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

cp "$SOURCE" "$RAW_DIR/un-m49-areas.json"
info "sha256: $(sha256_of "$RAW_DIR/un-m49-areas.json")"

info "json → ndjson (jq)"
jq -c '.[]' "$RAW_DIR/un-m49-areas.json" > "$DATA_DIR/areas.ndjson"

finalize_ndjson "$DATA_DIR/areas.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/un_m49_areas.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/un-m49-areas.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Every Least-Developed Country in Sub-Saharan Africa
  SELECT name FROM un_m49_areas
  WHERE least_developed AND sub_region_name = 'Sub-Saharan Africa'
  ORDER BY name;

  # Roll GeoNames country population up to UN sub-region
  SELECT m.sub_region_name, SUM(ci.population) AS pop, COUNT(*) AS countries
  FROM geonames_country_info ci
  JOIN un_m49_areas m USING PLACE
  WHERE m.sub_region_name IS NOT NULL
  GROUP BY m.sub_region_name
  ORDER BY pop DESC;

EOF
