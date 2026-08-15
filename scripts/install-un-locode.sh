#!/usr/bin/env bash
# UN/LOCODE — UN Code for Trade and Transport Locations. Every port,
# airport, rail terminal, and inland location UN/CEFACT knows about
# (~110 000 entries across 249 countries). Free & CC0.
#
# The canonical dump is a zip of per-country CSVs shipped from
# unece.org. We stream every file into a single NDJSON.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="un-locode"
# UN publishes new revisions twice a year — override to pin a version.
: "${HITORRO_UN_LOCODE_URL:=https://service.unece.org/trade/locode/loc242csv.zip}"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd unzip
require_cmd python3

fetch "$HITORRO_UN_LOCODE_URL" "$RAW_DIR/loclist.zip"
info "sha256: $(sha256_of "$RAW_DIR/loclist.zip")"

info "unzip"
unzip -qo "$RAW_DIR/loclist.zip" -d "$RAW_DIR"

info "csv → ndjson (streaming across every country file)"
# CSV columns per UN spec:
# ChangeType,Country,Location,Name,NameWoDiacritics,Subdivision,Status,
# Function,Date,IATA,Coordinates,Remarks
python3 - "$RAW_DIR" "$DATA_DIR/locode.ndjson" <<'PY'
import csv, glob, json, os, sys, re

raw_dir, out_path = sys.argv[1], sys.argv[2]

COLS = ['ChangeType','Country','Location','Name','NameWoDiacritics',
        'Subdivision','Status','Function','Date','IATA','Coordinates','Remarks']

def parse_coords(s):
    # UN LOCODE coord format: "3742N 12525W" or "0107S 12345E"
    if not s: return None, None
    m = re.match(r'^(\d{2})(\d{2})([NS])\s+(\d{3})(\d{2})([EW])$', s.strip())
    if not m: return None, None
    lat = int(m.group(1)) + int(m.group(2)) / 60.0
    if m.group(3) == 'S': lat = -lat
    lon = int(m.group(4)) + int(m.group(5)) / 60.0
    if m.group(6) == 'W': lon = -lon
    return lat, lon

n = 0
with open(out_path, 'w') as out:
    for path in sorted(glob.glob(os.path.join(raw_dir, '*.csv'))):
        with open(path, encoding='latin-1', errors='replace') as f:
            reader = csv.reader(f)
            for row in reader:
                if len(row) < len(COLS): continue
                r = dict(zip(COLS, row))
                # locode = Country + Location (both are stable codes)
                if not r['Country'] or not r['Location']: continue
                lat, lon = parse_coords(r.get('Coordinates', ''))
                out.write(json.dumps({
                    'locode':         r['Country'] + r['Location'],
                    'country':        r['Country'],
                    'location_code':  r['Location'],
                    'name':           r['Name'],
                    'name_ascii':     r['NameWoDiacritics'],
                    'subdivision':    r.get('Subdivision') or None,
                    'status':         r.get('Status') or None,
                    'function':       r.get('Function') or None,
                    'iata':           r.get('IATA') or None,
                    'latitude':       lat,
                    'longitude':      lon,
                    'change_type':    r.get('ChangeType') or None,
                    'date':           r.get('Date') or None,
                }) + '\n')
                n += 1
print(f'wrote {n} rows', file=sys.stderr)
PY

finalize_ndjson "$DATA_DIR/locode.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/un_locode.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/un-locode.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # How many LOCODEs per country?
  SELECT country, COUNT(*) AS n FROM un_locode
  GROUP BY country ORDER BY n DESC LIMIT 20;

  # Every airport LOCODE (function code contains '4' = airport)
  SELECT locode, name, country, iata FROM un_locode
  WHERE function LIKE '%4%' AND iata IS NOT NULL LIMIT 20;

EOF
