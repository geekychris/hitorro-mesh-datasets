#!/usr/bin/env bash
# ISO 3166-2 — country subdivisions (states, provinces, regions,
# emirates, oblasts, …). ~5000 entries covering ~250 countries.
#
# Source: the community-maintained JSON at datahub.io (also mirrors
# the UN/ISO official CSVs). CC0.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="iso-3166-2"
URL="https://raw.githubusercontent.com/olahol/iso-3166-2.json/master/iso-3166-2.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

fetch "$URL" "$RAW_DIR/iso-3166-2.json"
info "sha256: $(sha256_of "$RAW_DIR/iso-3166-2.json")"

info "json → ndjson (flatten country + divisions)"
# Shape: { "US": { name, divisions: { "US-CA": "California", ... } } }
jq -c '
  to_entries[] as $c |
  $c.value.divisions | to_entries[] |
  {
    code:         .key,
    name:         .value,
    country_code: ($c.key),
    country_name: ($c.value.name)
  }
' "$RAW_DIR/iso-3166-2.json" > "$DATA_DIR/subdivisions.ndjson"

finalize_ndjson "$DATA_DIR/subdivisions.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/iso_3166_2.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/iso-3166-2.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Every US state / territory
  SELECT code, name FROM iso_3166_2 WHERE country_code = 'US' ORDER BY name;

  # Country with most subdivisions
  SELECT country_code, country_name, COUNT(*) AS n
  FROM iso_3166_2 GROUP BY country_code ORDER BY n DESC LIMIT 10;

EOF
