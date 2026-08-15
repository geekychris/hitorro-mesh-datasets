#!/usr/bin/env bash
# ISO 639-3 — every language SIL has a code for (~8000). Superset of
# ISO 639-1 (which we already ship as iso-languages, ~180 entries).
#
# Source: sil.org/iso639-3 — canonical tab-separated download.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="iso-639-3"
URL="https://iso639-3.sil.org/sites/iso639-3/files/downloads/iso-639-3.tab"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

fetch "$URL" "$RAW_DIR/iso-639-3.tab"
info "sha256: $(sha256_of "$RAW_DIR/iso-639-3.tab")"

# TSV columns per SIL:
# Id  Part2B  Part2T  Part1  Scope  Language_Type  Ref_Name  Comment
COLS="alpha3,alpha3_bibliographic,alpha3_terminologic,alpha2,scope,language_type,reference_name,comment"

tsv_to_ndjson "$RAW_DIR/iso-639-3.tab" "$DATA_DIR/languages.ndjson" "$COLS" ""

cp "$MODULE_ROOT/src/main/resources/types/iso_639_3.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/iso-639-3.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Extinct vs living vs constructed
  SELECT language_type, COUNT(*) AS n
  FROM iso_639_3 GROUP BY language_type ORDER BY n DESC;

  # Individual languages missing an ISO 639-1 code (the vast majority)
  SELECT alpha3, reference_name FROM iso_639_3
  WHERE alpha2 IS NULL AND scope = 'I' AND language_type = 'L' LIMIT 20;

EOF
