#!/usr/bin/env bash
# USGS earthquakes — last 30 days feed, updated by USGS every ~15 minutes.
# Public domain. About 10 000-15 000 rows depending on activity.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="usgs-earthquakes"
URL="https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_month.geojson"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

fetch "$URL" "$RAW_DIR/all_month.geojson"
info "sha256: $(sha256_of "$RAW_DIR/all_month.geojson")"

info "geojson → ndjson (jq)"
# USGS coordinates are [longitude, latitude, depth-km]. Tsunami is 0/1 as a
# JSON number in the source; keep as core_long. Alert is null for most
# events; preserve as null so downstream WHERE alert = 'orange' works.
jq -c '.features[] | {
    event_id:      .id,
    magnitude:     (.properties.mag         // null),
    place:         (.properties.place       // null),
    time_millis:   (.properties.time        // null),
    longitude:     (.geometry.coordinates[0] // null),
    latitude:      (.geometry.coordinates[1] // null),
    depth_km:      (.geometry.coordinates[2] // null),
    tsunami:       (.properties.tsunami     // 0),
    significance:  (.properties.sig         // null),
    alert:         (.properties.alert       // null),
    event_type:    (.properties.type        // null),
    url:           (.properties.url         // null),
    title:         (.properties.title       // null)
  }' "$RAW_DIR/all_month.geojson" > "$DATA_DIR/events.ndjson"
ok "wrote $(wc -l < "$DATA_DIR/events.ndjson") records to $DATA_DIR/events.ndjson"

cp "$MODULE_ROOT/src/main/resources/types/usgs_earthquakes.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/usgs-earthquakes.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/all_month.geojson
    data/events.ndjson
    types/usgs_earthquakes.json

Refresh anytime with HITORRO_DATASETS_FORCE=1 ./scripts/install-usgs-earthquakes.sh

Queries to try:

  # Every event over M6.0 this month
  SELECT title, magnitude, place, alert
  FROM usgs_earthquakes
  WHERE magnitude >= 6
  ORDER BY magnitude DESC LIMIT 20;

  # Where the deep events happen
  SELECT title, depth_km, magnitude
  FROM usgs_earthquakes
  WHERE depth_km > 300
  ORDER BY depth_km DESC LIMIT 20;

  # Tsunami-flagged events
  SELECT title, magnitude, place, url
  FROM usgs_earthquakes
  WHERE tsunami = 1
  ORDER BY magnitude DESC LIMIT 20;

EOF
