#!/usr/bin/env bash
# PyPI top-200 packages by monthly download count.
# Sourced from hugovk/top-pypi-packages GitHub Pages JSON (updated ~monthly).
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="pypi-top-packages"
URL="https://hugovk.github.io/top-pypi-packages/top-pypi-packages.min.json"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

fetch "$URL" "$RAW_DIR/pypi.json"
info "sha256: $(sha256_of "$RAW_DIR/pypi.json")"

info "json → ndjson (jq) — top 200 slice"
# Source shape: { last_update, rows: [{ download_count, project }, ...] }
# Rows are already sorted by download_count desc; slice to first 200 and
# add a 1-based rank. Stamp source_last_update on every row for lineage.
jq -c --arg src_ts "$(jq -r '.last_update' "$RAW_DIR/pypi.json")" '
    .rows[0:200] | to_entries | .[] | {
        package_rank:       (.key + 1),
        package_name:       .value.project,
        monthly_downloads:  .value.download_count,
        source_last_update: $src_ts
    }
' "$RAW_DIR/pypi.json" > "$DATA_DIR/packages.ndjson"

finalize_ndjson "$DATA_DIR/packages.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/pypi_top_packages.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/pypi-top-packages.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # The top 10
  SELECT package_rank, package_name, monthly_downloads
  FROM pypi_top_packages ORDER BY package_rank LIMIT 10;

  # AWS / boto ecosystem
  SELECT package_rank, package_name, monthly_downloads
  FROM pypi_top_packages
  WHERE package_name LIKE '%aws%' OR package_name LIKE 'boto%'
  ORDER BY package_rank;

  # Numeric-computing stack
  SELECT package_rank, package_name, monthly_downloads
  FROM pypi_top_packages
  WHERE package_name IN ('numpy','scipy','pandas','matplotlib','scikit-learn','torch','tensorflow')
  ORDER BY monthly_downloads DESC;

  # 'Which packages are downloaded more than a billion times a month?'
  SELECT package_rank, package_name, monthly_downloads
  FROM pypi_top_packages
  WHERE monthly_downloads > 1000000000 ORDER BY package_rank;

EOF
