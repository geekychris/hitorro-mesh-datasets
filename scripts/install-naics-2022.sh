#!/usr/bin/env bash
# NAICS 2022 — curated JSON (sectors + subsectors) that ships in the module.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="naics-2022"
SOURCE="$MODULE_ROOT/src/main/resources/data/naics-2022.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

cp "$SOURCE" "$RAW_DIR/naics-2022.json"
info "sha256: $(sha256_of "$RAW_DIR/naics-2022.json")"

info "json → ndjson (jq)"
jq -c '.[]' "$RAW_DIR/naics-2022.json" > "$DATA_DIR/naics.ndjson"

finalize_ndjson "$DATA_DIR/naics.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/naics_2022.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/naics-2022.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # All 24 top-level sectors
  SELECT code, title FROM naics_2022 WHERE level = 2 ORDER BY code;

  # Every subsector of Manufacturing (31, 32, 33)
  SELECT code, title FROM naics_2022
  WHERE parent_code IN ('31','32','33')
  ORDER BY code;

  # Self-join: sector name next to each subsector
  SELECT sub.code, sub.title AS subsector, sec.title AS sector
  FROM naics_2022 sub
  JOIN naics_2022 sec ON sub.parent_code = sec.code
  WHERE sub.level = 3
  ORDER BY sub.code;

EOF
