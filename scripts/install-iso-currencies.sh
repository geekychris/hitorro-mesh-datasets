#!/usr/bin/env bash
# ISO 4217 currencies — curated JSON that ships in the module resources.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="iso-currencies"
SOURCE="$MODULE_ROOT/src/main/resources/data/iso-4217-currencies.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

cp "$SOURCE" "$RAW_DIR/iso-4217-currencies.json"
info "sha256: $(sha256_of "$RAW_DIR/iso-4217-currencies.json")"

info "json → ndjson (jq)"
jq -c '.[]' "$RAW_DIR/iso-4217-currencies.json" > "$DATA_DIR/currencies.ndjson"

finalize_ndjson "$DATA_DIR/currencies.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/iso_currencies.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/iso-currencies.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Every 0-decimal currency (money without cents)
  SELECT alpha3, name, symbol FROM iso_currencies
  WHERE minor_unit = 0 ORDER BY name;

  # Cross with GeoNames country info via currency
  SELECT ci.country, ci.capital, cur.alpha3, cur.name, cur.symbol
  FROM geonames_country_info ci
  JOIN iso_currencies cur USING PLACE
  ORDER BY ci.country LIMIT 20;

EOF
