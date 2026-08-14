#!/usr/bin/env bash
# HuggingFace top-100 models by download count. One API call, no auth.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="huggingface-top-models"
URL="https://huggingface.co/api/models?sort=downloads&direction=-1&limit=100&full=true"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

fetch "$URL" "$RAW_DIR/models.json"
info "sha256: $(sha256_of "$RAW_DIR/models.json")"

info "json → ndjson (jq)"
# Response is a top-level array of models. Tags is a flat array carrying
# multiple prefix-namespaced entries — pick the first matching each
# family (license:, language:, dataset:) as a summary. Full tag list
# preserved in raw/models.json.
jq -c '.[] | . as $m
    | ($m.tags // []) as $tags
    | {
        model_id:     $m.id,
        author:       ($m.author // ($m.id | split("/")[0])),
        downloads:    ($m.downloads // 0),
        likes:        ($m.likes // 0),
        pipeline_tag: ($m.pipeline_tag // null),
        library_name: ($m.library_name // null),
        license_tag:  ([$tags[] | select(startswith("license:"))] | first  // null),
        language_tag: ([$tags[] | select(startswith("language:"))] | first // null),
        dataset_tag:  ([$tags[] | select(startswith("dataset:"))] | first  // null),
        tag_count:    ($tags | length),
        created_at:   ($m.createdAt // null)
    }
' "$RAW_DIR/models.json" > "$DATA_DIR/models.ndjson"

finalize_ndjson "$DATA_DIR/models.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/huggingface_top_models.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/huggingface-top-models.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Top 10 by downloads
  SELECT model_id, downloads, pipeline_tag, library_name
  FROM huggingface_top_models ORDER BY downloads DESC LIMIT 10;

  # Task distribution in top 100
  SELECT pipeline_tag, COUNT(*) AS n
  FROM huggingface_top_models
  WHERE pipeline_tag IS NOT NULL
  GROUP BY pipeline_tag ORDER BY n DESC;

  # Which libraries dominate?
  SELECT library_name, COUNT(*) AS n, SUM(downloads) AS total_dl
  FROM huggingface_top_models
  WHERE library_name IS NOT NULL
  GROUP BY library_name ORDER BY total_dl DESC;

EOF
