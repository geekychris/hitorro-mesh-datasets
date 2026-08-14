#!/usr/bin/env bash
# OpenStreetMap airports (with IATA codes) via the public Overpass API.
# Query is a single POST; response is ~4 000 nodes worldwide, ~1 MB.
# Overpass caches heavily so this is fast on repeat runs.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="osm-airports"
OVERPASS_URL="https://overpass-api.de/api/interpreter"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

# Overpass QL — every OSM node tagged aeroway=aerodrome with a non-empty
# iata tag. `out;` returns full node objects (id, lat, lon, tags).
QUERY='[out:json][timeout:120];
node["aeroway"="aerodrome"]["iata"];
out;'

if [[ -f "$RAW_DIR/airports.json" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
    info "cached: $RAW_DIR/airports.json — set HITORRO_DATASETS_FORCE=1 to refresh"
else
    info "Overpass → $OVERPASS_URL (~10-30 s)"
    curl -sSf -X POST "$OVERPASS_URL" \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        --data-urlencode "data=$QUERY" \
        -o "$RAW_DIR/airports.json.part"
    mv "$RAW_DIR/airports.json.part" "$RAW_DIR/airports.json"
fi
info "sha256: $(sha256_of "$RAW_DIR/airports.json")"

info "overpass-json → ndjson (jq)"
# OSM tags live under .tags — pull the ones we care about, keeping nulls
# where absent. Some airports have "IATA" values that are actually longer
# codes like "" (empty) or comma-separated ("KHV,KHV1") — filter to
# strict 3-letter uppercase.
jq -c '
    .elements[]
    | . as $e
    | ($e.tags // {}) as $t
    | ($t.iata // "") as $iata
    | select($iata | test("^[A-Z]{3}$"))
    | {
        iata_code:    $iata,
        icao_code:    ($t.icao // null),
        osm_id:       $e.id,
        name:         ($t.name // null),
        name_en:      ($t["name:en"] // null),
        latitude:     $e.lat,
        longitude:    $e.lon,
        country_code: ($t["addr:country"] // $t["is_in:country_code"] // null),
        wikidata_qid: ($t.wikidata // null)
    }
' "$RAW_DIR/airports.json" > "$DATA_DIR/airports.ndjson"

finalize_ndjson "$DATA_DIR/airports.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/osm_airports.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/osm-airports.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Every airport in a country by IATA count
  SELECT country_code, COUNT(*) AS n_airports
  FROM osm_airports
  GROUP BY country_code ORDER BY n_airports DESC LIMIT 20;

  # Which airports have Wikidata cross-references?
  SELECT iata_code, name, wikidata_qid
  FROM osm_airports
  WHERE wikidata_qid IS NOT NULL LIMIT 20;

  # Semantic — airports joined to World Bank socioeconomics via country
  # Note: this join includes an ODbL source, so the combined result carries
  # share-alike obligations. Check the driver log for the LicenseAlgebra
  # warning when this lands with license-aware responses (see ROADMAP).
  SELECT a.iata_code, a.name, wb.gdp_per_capita_usd
  FROM osm_airports a
  JOIN wikidata_countries wc USING PLACE
  JOIN worldbank_indicators wb USING PLACE
  WHERE wb.gdp_per_capita_usd > 40000
  ORDER BY wb.gdp_per_capita_usd DESC LIMIT 20;

EOF
