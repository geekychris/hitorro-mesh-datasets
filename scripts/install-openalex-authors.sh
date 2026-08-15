#!/usr/bin/env bash
# OpenAlex Authors — 100M+ people in scholarly publishing. Default pulls
# 5 pages of top-cited authors globally. Tune to slice by institution or
# concept.
#
# Tunables:
#   HITORRO_OPENALEX_AUTHORS_PAGES=5           # 200/page (max)
#   HITORRO_OPENALEX_AUTHORS_INSTITUTION=<id>  # optional filter (e.g. I27837315 = MIT)
#   HITORRO_OPENALEX_AUTHORS_CONCEPT=<id>      # optional concept filter
#   HITORRO_OPENALEX_MAILTO=you@example.com
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

: "${HITORRO_OPENALEX_AUTHORS_PAGES:=5}"
: "${HITORRO_OPENALEX_AUTHORS_INSTITUTION:=}"
: "${HITORRO_OPENALEX_AUTHORS_CONCEPT:=}"
: "${HITORRO_OPENALEX_MAILTO:=chris@hitorro.com}"

DATASET_ID="openalex-authors"
INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

FILTER=""
if [[ -n "$HITORRO_OPENALEX_AUTHORS_INSTITUTION" ]]; then
    FILTER="last_known_institution.id:${HITORRO_OPENALEX_AUTHORS_INSTITUTION}"
fi
if [[ -n "$HITORRO_OPENALEX_AUTHORS_CONCEPT" ]]; then
    [[ -n "$FILTER" ]] && FILTER="${FILTER},"
    FILTER="${FILTER}x_concepts.id:${HITORRO_OPENALEX_AUTHORS_CONCEPT}"
fi

info "Pulling ${HITORRO_OPENALEX_AUTHORS_PAGES} page(s) x 200 authors — filter=${FILTER:-(none, top-cited globally)}"

cursor='*'
for page in $(seq 1 "$HITORRO_OPENALEX_AUTHORS_PAGES"); do
    raw="$RAW_DIR/authors-page${page}.json"
    if [[ -f "$raw" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
        info "cached: $(basename "$raw")"
        cursor=$(jq -r '.meta.next_cursor // "*"' "$raw")
        [[ "$cursor" == "null" || -z "$cursor" ]] && cursor='*'
        continue
    fi
    info "page $page (cursor=${cursor:0:20}...)"
    args=(--get "https://api.openalex.org/authors"
          --data-urlencode "per-page=200"
          --data-urlencode "cursor=${cursor}"
          --data-urlencode "sort=cited_by_count:desc"
          --data-urlencode "mailto=${HITORRO_OPENALEX_MAILTO}")
    [[ -n "$FILTER" ]] && args+=(--data-urlencode "filter=${FILTER}")
    curl -sSf \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (mailto:${HITORRO_OPENALEX_MAILTO})" \
        "${args[@]}" -o "$raw.part"
    mv "$raw.part" "$raw"
    cursor=$(jq -r '.meta.next_cursor // "*"' "$raw")
    if [[ "$cursor" == "null" || -z "$cursor" ]]; then
        info "no more pages after $page"
        break
    fi
done

info "json → ndjson (jq)"
jq -sc '
    map(.results[]) | flatten
    | .[]
    | {
        openalex_id:            (.id | sub("https://openalex.org/"; "")),
        orcid:                  (.orcid // null),
        display_name:           .display_name,
        works_count:            .works_count,
        cited_by_count:         .cited_by_count,
        h_index:                (.summary_stats.h_index // null),
        i10_index:              (.summary_stats.i10_index // null),
        institution_id:         ((.last_known_institution // {}).id // null | if . then sub("https://openalex.org/"; "") else null end),
        institution_display:    ((.last_known_institution // {}).display_name // null),
        institution_country:    ((.last_known_institution // {}).country_code // null),
        top_concept:            ((.x_concepts // [])[0].display_name // null)
    }' "$RAW_DIR"/authors-page*.json > "$DATA_DIR/authors.ndjson"

finalize_ndjson "$DATA_DIR/authors.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/openalex_authors.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/openalex-authors.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # H-index leaderboard
  SELECT display_name, h_index, cited_by_count, institution_display
  FROM openalex_authors ORDER BY h_index DESC LIMIT 20;

Slice by institution:
  HITORRO_OPENALEX_AUTHORS_INSTITUTION=I27837315 ./scripts/install-openalex-authors.sh

EOF
