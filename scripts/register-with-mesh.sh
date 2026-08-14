#!/usr/bin/env bash
# Announce an already-installed dataset to a running mesh driver via
# POST /mesh/tables (or /mesh/broadcast-tables with --broadcast).
#
# Note: this does NOT push data to agents. The agent must already have been
# started with the ndjson-file + type-json-resource under its broadcast-tables
# (broadcast) or tables (distributed) config. The registrar only tells the
# driver's planner that the table exists so queries can reference it.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DRIVER_URL="${MESH_DRIVER_URL:-http://localhost:8085}"
BROADCAST=0
DATASET_ID=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --broadcast) BROADCAST=1; shift ;;
        --driver)    DRIVER_URL="$2"; shift 2 ;;
        --*) die "unknown flag: $1" ;;
        *) DATASET_ID="$1"; shift ;;
    esac
done

[[ -n "$DATASET_ID" ]] || die "usage: $0 [--broadcast] [--driver URL] <dataset-id>"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
[[ -d "$INSTALL_DIR" ]] || die "not installed: $INSTALL_DIR (run install-$DATASET_ID.sh first)"

TABLE_NAME="${DATASET_ID//-/_}"
TYPE_JSON="$INSTALL_DIR/types/${TABLE_NAME}.json"
[[ -f "$TYPE_JSON" ]] || die "missing type file: $TYPE_JSON"

require_cmd curl

if [[ "$BROADCAST" == "1" ]]; then
    info "POST $DRIVER_URL/mesh/broadcast-tables  name=$TABLE_NAME"
    curl -sSf -X POST "$DRIVER_URL/mesh/broadcast-tables" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$TABLE_NAME\"}"
    ok "broadcast table $TABLE_NAME registered"
else
    PARTITION_KEY="${DATASET_PARTITION:-all}"
    BODY="$(cat <<JSON
{
  "name": "$TABLE_NAME",
  "typeJsonResource": "file:$TYPE_JSON",
  "partitions": [
    { "key": "$PARTITION_KEY",
      "requiredCapabilities": ["jvssql", "partition:$TABLE_NAME:$PARTITION_KEY"] }
  ]
}
JSON
)"
    info "POST $DRIVER_URL/mesh/tables  name=$TABLE_NAME partition=$PARTITION_KEY"
    curl -sSf -X POST "$DRIVER_URL/mesh/tables" \
        -H "Content-Type: application/json" \
        -d "$BODY"
    ok "distributed table $TABLE_NAME registered"
fi

printf "\nCheck: curl -s %s/mesh/tables | jq .\n" "$DRIVER_URL"
