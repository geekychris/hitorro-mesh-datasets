#!/usr/bin/env bash
# HTS — US Harmonized Tariff Schedule (US International Trade
# Commission). ~19 000 codes across chapters 01-99. JSON dump refreshed
# ~quarterly; pin a revision via HITORRO_HTS_YEAR + REV.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="hts-tariff"
: "${HITORRO_HTS_YEAR:=2025}"
: "${HITORRO_HTS_REV:=Basic}"

URL="https://hts.usitc.gov/reststop/exportList?from=0100000000&to=9999999999&format=JSON&styles=false"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"
require_cmd jq

fetch "$URL" "$RAW_DIR/hts.json"
info "sha256: $(sha256_of "$RAW_DIR/hts.json")"

info "json → ndjson"
jq -c '.[] | {
  hts_number:     .htsno,
  indent:         (.indent | tonumber? // null),
  description:    .description,
  unit_of_qty:    (.units | join(", ") // null),
  general_rate:   .general,
  special_rate:   .special,
  other_rate:     .other,
  quota:          .quota,
  additional_dut: .additionalDuty,
  addressed_by:   .addressedBy,
  footnotes:      (.footnotes | tojson)
}' "$RAW_DIR/hts.json" > "$DATA_DIR/hts.ndjson"

finalize_ndjson "$DATA_DIR/hts.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/hts_tariff.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/hts-tariff.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Every chapter-level entry (2-digit HTS)
  SELECT hts_number, description FROM hts_tariff
  WHERE indent = 0 ORDER BY hts_number;

EOF
