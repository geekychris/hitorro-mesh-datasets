#!/usr/bin/env bash
# FIPS 6-4 — US counties + equivalent (~3200). Codes withdrawn by NIST
# in 2008 in favor of ANSI/INCITS 31 but still ubiquitous. Census
# ships the current file in the ANSI/GNIS national gazetteer.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="fips-counties"
URL="https://www2.census.gov/geo/docs/reference/codes2020/national_county2020.txt"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

fetch "$URL" "$RAW_DIR/national_county2020.txt"
info "sha256: $(sha256_of "$RAW_DIR/national_county2020.txt")"

# Census pipe-delimited: STATE|STATEFP|COUNTYFP|COUNTYNS|COUNTYNAME|CLASSFP|FUNCSTAT
info "psv → ndjson (streaming)"
python3 - "$RAW_DIR/national_county2020.txt" "$DATA_DIR/counties.ndjson" <<'PY'
import json, sys
src, dst = sys.argv[1], sys.argv[2]
with open(src, encoding='utf-8') as f, open(dst, 'w') as out:
    header = f.readline().strip().split('|')
    for line in f:
        parts = line.rstrip('\n').split('|')
        if len(parts) < len(header): continue
        row = dict(zip(header, parts))
        out.write(json.dumps({
            'state':         row['STATE'],
            'state_fips':    row['STATEFP'],
            'county_fips':   row['COUNTYFP'],
            'county_ansi':   row['COUNTYNS'],
            'county_name':   row['COUNTYNAME'],
            'class_fips':    row['CLASSFP'],
            'functional':    row['FUNCSTAT'],
            'geoid':         row['STATEFP'] + row['COUNTYFP']
        }) + '\n')
PY

finalize_ndjson "$DATA_DIR/counties.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/fips_counties.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/fips-counties.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Counties per state
  SELECT state, COUNT(*) AS n FROM fips_counties
  GROUP BY state ORDER BY n DESC LIMIT 10;

  # All counties named 'Washington'
  SELECT state, county_name FROM fips_counties WHERE county_name LIKE '%Washington%';

EOF
