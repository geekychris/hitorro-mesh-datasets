#!/usr/bin/env bash
# NASA Astronomy Picture of the Day — 30 random samples.
# DEMO_KEY works for this cadence. Set NASA_API_KEY for higher limits.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="nasa-apod"
API_KEY="${NASA_API_KEY:-DEMO_KEY}"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

fetch "https://api.nasa.gov/planetary/apod?api_key=${API_KEY}&count=30" "$RAW_DIR/apod.json"
info "sha256: $(sha256_of "$RAW_DIR/apod.json")"

info "json → ndjson (jq)"
# Preserve nulls for hdurl (video entries) and copyright (public-domain
# NASA-owned images). Clean up trailing whitespace in explanation.
jq -c '.[] | {
    apod_date:       .date,
    title:           .title,
    explanation:     (.explanation | gsub("[[:space:]]+$"; "")),
    url:             (.url // null),
    hdurl:           (.hdurl // null),
    media_type:      .media_type,
    copyright:       ((.copyright // null) | if . == null then null else (. | gsub("^[[:space:]]+|[[:space:]]+$"; "")) end),
    service_version: .service_version
}' "$RAW_DIR/apod.json" > "$DATA_DIR/apod.ndjson"

finalize_ndjson "$DATA_DIR/apod.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/nasa_apod.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/nasa-apod.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Public-domain NASA-owned APODs (no third-party copyright)
  SELECT apod_date, title FROM nasa_apod
  WHERE copyright IS NULL AND media_type = 'image'
  ORDER BY apod_date DESC LIMIT 10;

  # Every video in the sample
  SELECT apod_date, title, url FROM nasa_apod
  WHERE media_type = 'video' ORDER BY apod_date DESC;

  # Who's the most-credited amateur astronomer?
  SELECT copyright, COUNT(*) AS n
  FROM nasa_apod WHERE copyright IS NOT NULL
  GROUP BY copyright ORDER BY n DESC LIMIT 10;

Refresh:  HITORRO_DATASETS_FORCE=1 ./scripts/install-nasa-apod.sh
Higher limits: NASA_API_KEY=your_key ./scripts/install-nasa-apod.sh

EOF
