#!/usr/bin/env bash
# NPM — weekly download counts for a curated list of well-known packages.
# One bulk HTTP call to api.npmjs.org, no auth. Refresh weekly-ish.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="npm-top-packages"
PKG_LIST="$MODULE_ROOT/src/main/resources/data/npm-top-package-list.txt"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

# api.npmjs.org's bulk endpoint accepts comma-separated names, but
# refuses scoped packages ("@scope/name") in bulk — "scoped packages are
# not currently supported in bulk lookups". Split the list: bulk-fetch
# every unscoped package in one call, then fetch scoped ones individually
# and merge into the same JSON map.
all_pkgs=$(grep -v '^\s*$\|^\s*#' "$PKG_LIST")
unscoped=$(echo "$all_pkgs" | grep -v '^@' | tr '\n' ',' | sed 's/,$//')
scoped=$(echo "$all_pkgs" | grep '^@' || true)

info "bulk fetch: $(echo "$unscoped" | tr , '\n' | wc -l | tr -d ' ') unscoped packages"
curl -sSfL "https://api.npmjs.org/downloads/point/last-week/${unscoped}" \
    -o "$RAW_DIR/downloads-bulk.json"

# Start the combined file from the bulk result. Then append each scoped
# lookup as a single top-level entry keyed on the package name.
jq . "$RAW_DIR/downloads-bulk.json" > "$RAW_DIR/downloads.json"
for pkg in $scoped; do
    encoded=$(printf %s "$pkg" | sed 's|@|%40|;s|/|%2F|')
    info "fetching scoped: $pkg"
    # Merge into downloads.json in-place; jq's object-add operator
    # produces { existing..., "@scope/name": {downloads,...} }.
    resp=$(curl -sSf "https://api.npmjs.org/downloads/point/last-week/${encoded}" || echo 'null')
    jq --arg k "$pkg" --argjson v "$resp" '. + {($k): $v}' "$RAW_DIR/downloads.json" \
        > "$RAW_DIR/downloads.json.new" && mv "$RAW_DIR/downloads.json.new" "$RAW_DIR/downloads.json"
done
info "sha256: $(sha256_of "$RAW_DIR/downloads.json")"

info "json → ndjson (jq) — rank by weekly_downloads desc"
# Response is a keyed map {pkgname: {downloads, package, start, end}, ...}.
# Skip null entries (packages the endpoint didn't know about) and sort
# by downloads desc, then emit a 1-based package_rank per row.
jq -c '
    to_entries
    | map(select(.value != null))
    | sort_by(-.value.downloads)
    | to_entries
    | .[] | {
        package_rank:     (.key + 1),
        package_name:     .value.value.package,
        weekly_downloads: .value.value.downloads,
        week_start:       .value.value.start,
        week_end:         .value.value.end
    }
' "$RAW_DIR/downloads.json" > "$DATA_DIR/packages.ndjson"

finalize_ndjson "$DATA_DIR/packages.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/npm_top_packages.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/npm-top-packages.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # This week's leaders
  SELECT package_rank, package_name, weekly_downloads
  FROM npm_top_packages ORDER BY package_rank LIMIT 10;

  # UI toolkits
  SELECT package_name, weekly_downloads FROM npm_top_packages
  WHERE package_name IN ('react','vue','@mui/material','tailwindcss','styled-components','material-ui')
  ORDER BY weekly_downloads DESC;

  # HTTP clients
  SELECT package_name, weekly_downloads FROM npm_top_packages
  WHERE package_name IN ('axios','node-fetch','undici','request','ws')
  ORDER BY weekly_downloads DESC;

Add more packages by editing src/main/resources/data/npm-top-package-list.txt
in the repo and re-installing.

EOF
