#!/usr/bin/env bash
# OpenAlex top institutions by cited-by-count. Two pages of 200 each
# gets the top 400; per-page cap is 200. Small, plenty for demo.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="openalex-institutions"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

# Tunables — override at install to pull more pages:
#   HITORRO_OPENALEX_INSTITUTIONS_PAGES=2    # 200 per page (max)
#   HITORRO_OPENALEX_MAILTO=you@example.com  # rate-limit friendliness
: "${HITORRO_OPENALEX_INSTITUTIONS_PAGES:=2}"
: "${HITORRO_OPENALEX_MAILTO:=chris@hitorro.com}"

info "Pulling ${HITORRO_OPENALEX_INSTITUTIONS_PAGES} page(s) x 200 institutions"

# OpenAlex asks for a User-Agent + polite mailto in the query string; both
# earn faster / more predictable rate limits.
_pages=$(seq 1 "$HITORRO_OPENALEX_INSTITUTIONS_PAGES")
for page in $_pages; do
    raw="$RAW_DIR/institutions-page${page}.json"
    if [[ -f "$raw" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
        info "cached: $(basename "$raw")"
        continue
    fi
    info "fetching page $page..."
    curl -sSf \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets; mailto:${HITORRO_OPENALEX_MAILTO})" \
        "https://api.openalex.org/institutions?per-page=200&page=${page}&sort=cited_by_count:desc&mailto=${HITORRO_OPENALEX_MAILTO}" \
        -o "$raw.part"
    mv "$raw.part" "$raw"
done

info "json → ndjson (jq)"
# Merge both pages, keep only institutions with a country_code (some
# multi-national orgs and pre-country archives have none) and de-dupe
# on openalex_id defensively.
jq -sc '
    map(.results[]) | flatten | unique_by(.id) | .[]
    | select(.country_code != null)
    | {
        openalex_id:      (.id | sub("https://openalex.org/"; "")),
        ror_id:           (.ror // null),
        display_name:     .display_name,
        country_code:     .country_code,
        city:             ((.geo // {}).city // null),
        institution_type: .type,
        works_count:      .works_count,
        cited_by_count:   .cited_by_count,
        homepage_url:     .homepage_url
    }
' "$RAW_DIR"/institutions-page*.json > "$DATA_DIR/institutions.ndjson"

finalize_ndjson "$DATA_DIR/institutions.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/openalex_institutions.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/openalex-institutions.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Top 10 most-cited institutions worldwide
  SELECT display_name, country_code, city, cited_by_count, works_count
  FROM openalex_institutions
  ORDER BY cited_by_count DESC LIMIT 10;

  # Which countries dominate the top-400?
  SELECT country_code, COUNT(*) AS n, SUM(cited_by_count) AS total_citations
  FROM openalex_institutions
  GROUP BY country_code
  ORDER BY total_citations DESC LIMIT 20;

  # Semantic — institutions x World Bank GDP per capita
  SELECT o.display_name, o.city, o.cited_by_count, wb.gdp_per_capita_usd
  FROM openalex_institutions o
  JOIN wikidata_countries wc USING PLACE
  JOIN worldbank_indicators wb USING PLACE
  ORDER BY o.cited_by_count DESC LIMIT 20;

EOF
