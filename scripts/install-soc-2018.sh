#!/usr/bin/env bash
# SOC 2018 — Standard Occupational Classification (US Bureau of Labor
# Statistics). ~1000 detailed occupations across 23 major groups.
# Source: BLS structure XLSX; we pull the CSV mirror if available.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="soc-2018"
# BLS XLSX is not friendly to parse in bash; use the public "definitions"
# csv distributed alongside. If the URL 404s, override with:
#   HITORRO_SOC_URL=<direct csv url>
: "${HITORRO_SOC_URL:=https://www.bls.gov/soc/2018/soc_2018_definitions.csv}"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"
require_cmd python3

fetch "$HITORRO_SOC_URL" "$RAW_DIR/soc_2018_definitions.csv"
info "sha256: $(sha256_of "$RAW_DIR/soc_2018_definitions.csv")"

info "csv → ndjson"
python3 - "$RAW_DIR/soc_2018_definitions.csv" "$DATA_DIR/soc.ndjson" <<'PY'
import csv, json, sys
src, dst = sys.argv[1], sys.argv[2]
with open(src, encoding='utf-8-sig') as f, open(dst, 'w') as out:
    r = csv.DictReader(f)
    for row in r:
        # BLS column names sometimes have trailing spaces
        row = {k.strip(): (v or '').strip() for k, v in row.items()}
        code = row.get('SOC Code') or row.get('Code')
        title = row.get('SOC Title') or row.get('Title')
        if not code: continue
        out.write(json.dumps({
            'soc_code':    code,
            'title':       title,
            'definition':  row.get('SOC Definition') or row.get('Definition') or None,
            'major_group': code.split('-')[0] if '-' in code else None,
        }) + '\n')
PY

finalize_ndjson "$DATA_DIR/soc.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/soc_2018.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/soc-2018.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Occupations in major group 15 (Computer & Mathematical)
  SELECT soc_code, title FROM soc_2018
  WHERE major_group = '15' ORDER BY soc_code;

EOF
