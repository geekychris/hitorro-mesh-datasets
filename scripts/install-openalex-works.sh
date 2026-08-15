#!/usr/bin/env bash
# OpenAlex Works — scholarly papers, articles, etc. Paged via cursor.
#
# Default pulls 1 year × 5 pages × 200 = 1000 works. Bump limits to grow.
# Tunables:
#   HITORRO_OPENALEX_WORKS_YEAR=2024                 # publication year filter
#   HITORRO_OPENALEX_WORKS_PAGES=5                   # 200/page (max)
#   HITORRO_OPENALEX_WORKS_TYPE=article              # article | book | dataset | ...
#   HITORRO_OPENALEX_WORKS_CONCEPT=<openalex-concept-id>  # optional topic filter
#   HITORRO_OPENALEX_MAILTO=you@example.com
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

: "${HITORRO_OPENALEX_WORKS_YEAR:=2024}"
: "${HITORRO_OPENALEX_WORKS_PAGES:=5}"
: "${HITORRO_OPENALEX_WORKS_TYPE:=article}"
: "${HITORRO_OPENALEX_WORKS_CONCEPT:=}"
: "${HITORRO_OPENALEX_MAILTO:=chris@hitorro.com}"

DATASET_ID="openalex-works"
INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

FILTER="publication_year:${HITORRO_OPENALEX_WORKS_YEAR},type:${HITORRO_OPENALEX_WORKS_TYPE}"
if [[ -n "$HITORRO_OPENALEX_WORKS_CONCEPT" ]]; then
    FILTER="${FILTER},concepts.id:${HITORRO_OPENALEX_WORKS_CONCEPT}"
fi

info "Pulling ${HITORRO_OPENALEX_WORKS_PAGES} page(s) x 200 works — filter=${FILTER}"

# OpenAlex cursor pagination — first page uses '*', subsequent use the
# next_cursor returned by the API. Cache each page raw.
cursor='*'
for page in $(seq 1 "$HITORRO_OPENALEX_WORKS_PAGES"); do
    raw="$RAW_DIR/works-page${page}.json"
    if [[ -f "$raw" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
        info "cached: $(basename "$raw")"
        cursor=$(jq -r '.meta.next_cursor // "*"' "$raw")
        [[ "$cursor" == "null" || -z "$cursor" ]] && cursor='*'
        continue
    fi
    info "page $page (cursor=${cursor:0:20}...)"
    curl -sSf \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (mailto:${HITORRO_OPENALEX_MAILTO})" \
        --get "https://api.openalex.org/works" \
        --data-urlencode "filter=${FILTER}" \
        --data-urlencode "per-page=200" \
        --data-urlencode "cursor=${cursor}" \
        --data-urlencode "mailto=${HITORRO_OPENALEX_MAILTO}" \
        -o "$raw.part"
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
        openalex_id:      (.id | sub("https://openalex.org/"; "")),
        doi:              (.doi // null),
        title:            (.title // .display_name // null),
        publication_year: .publication_year,
        publication_date: .publication_date,
        work_type:        .type,
        cited_by_count:   .cited_by_count,
        is_oa:            (.open_access.is_oa // false),
        oa_url:           (.open_access.oa_url // null),
        language:         (.language // null),
        authors_count:    (.authorships // [] | length),
        first_author:     ((.authorships // [])[0].author.display_name // null),
        primary_topic:    ((.primary_topic // {}).display_name // null),
        primary_concept:  ((.concepts // [])[0].display_name // null)
    }' "$RAW_DIR"/works-page*.json > "$DATA_DIR/works.ndjson"

finalize_ndjson "$DATA_DIR/works.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/openalex_works.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/openalex-works.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID  filter=${FILTER}")

Try:
  # Top-cited works this year
  SELECT title, first_author, cited_by_count
  FROM openalex_works ORDER BY cited_by_count DESC LIMIT 20;

  # Language / topic breakdown
  SELECT language, COUNT(*) AS n FROM openalex_works GROUP BY language ORDER BY n DESC;

Pull more:
  HITORRO_OPENALEX_WORKS_PAGES=25 ./scripts/install-openalex-works.sh
  HITORRO_OPENALEX_WORKS_YEAR=2023 ./scripts/install-openalex-works.sh

EOF
