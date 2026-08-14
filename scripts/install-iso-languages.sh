#!/usr/bin/env bash
# ISO 639 languages — curated JSON that ships in the module resources.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="iso-languages"
SOURCE="$MODULE_ROOT/src/main/resources/data/iso-639-languages.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

cp "$SOURCE" "$RAW_DIR/iso-639-languages.json"
info "sha256: $(sha256_of "$RAW_DIR/iso-639-languages.json")"

info "json → ndjson (jq)"
jq -c '.[]' "$RAW_DIR/iso-639-languages.json" > "$DATA_DIR/languages.ndjson"

finalize_ndjson "$DATA_DIR/languages.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/iso_languages.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/iso-languages.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Every macrolanguage
  SELECT alpha2, alpha3, name, native_name FROM iso_languages
  WHERE scope = 'macrolanguage' ORDER BY name;

  # Native script for the top 15 most-spoken (rough order in source)
  SELECT alpha2, name, native_name FROM iso_languages LIMIT 15;
EOF
