#!/usr/bin/env bash
# Wikidata city → enwiki article-slug bridge. Same P31/population filter
# as wikidata-cities so the two align row-for-row (minus items without
# an enwiki article, which just don't return a row).
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="wikidata-city-sitelinks"
SPARQL_ENDPOINT="https://query.wikidata.org/sparql"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

QUERY='SELECT DISTINCT ?item ?article WHERE {
  ?item wdt:P31 wd:Q515.
  ?item wdt:P1082 ?population.
  FILTER(?population > 100000)
  ?article schema:about ?item ;
           schema:isPartOf <https://en.wikipedia.org/>.
}
LIMIT 20000'

if [[ -f "$RAW_DIR/sitelinks.json" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
    info "cached: $RAW_DIR/sitelinks.json — set HITORRO_DATASETS_FORCE=1 to refresh"
else
    info "SPARQL → $SPARQL_ENDPOINT"
    curl -sSf -X POST "$SPARQL_ENDPOINT" \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        -H "Accept: application/sparql-results+json" \
        --data-urlencode "query=$QUERY" \
        -o "$RAW_DIR/sitelinks.json.part"
    mv "$RAW_DIR/sitelinks.json.part" "$RAW_DIR/sitelinks.json"
fi
info "sha256: $(sha256_of "$RAW_DIR/sitelinks.json")"

info "sparql-json → ndjson (jq)"
# Article URLs come back like
#   https://en.wikipedia.org/wiki/San_Francisco
# The slug (post-/wiki/) matches wikipedia_pageviews.article exactly.
# The display title is the slug with underscores → spaces. URL-decoding
# handled by SPARQL/curl.
jq -c '.results.bindings[] | {
    wikidata_qid:   (.item.value    | sub("http://www.wikidata.org/entity/"; "")),
    enwiki_article: (.article.value | sub("https://en.wikipedia.org/wiki/"; "")),
    enwiki_title:   (.article.value | sub("https://en.wikipedia.org/wiki/"; "") | gsub("_"; " ")),
    enwiki_url:     .article.value
}' "$RAW_DIR/sitelinks.json" > "$DATA_DIR/sitelinks.ndjson"
finalize_ndjson "$DATA_DIR/sitelinks.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/wikidata_city_sitelinks.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/wikidata-city-sitelinks.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Which of yesterday's top Wikipedia articles are actually cities?
  SELECT wp.rank, wp.title, wp.views, wd.population, wd.country_iso
  FROM wikipedia_pageviews wp
  JOIN wikidata_city_sitelinks sl ON wp.article = sl.enwiki_article
  JOIN wikidata_cities wd ON sl.wikidata_qid = wd.wikidata_qid
  ORDER BY wp.views DESC LIMIT 30;

  # Semantic version (rewriter picks the same joins via USING PLACE):
  SELECT wp.rank, wp.title, wp.views, wd.population
  FROM wikipedia_pageviews wp
  JOIN wikidata_city_sitelinks sl USING PLACE
  JOIN wikidata_cities wd USING PLACE
  ORDER BY wp.views DESC LIMIT 30;

  # Big cities that Wikipedia hardly noticed today
  SELECT wd.name, wd.population, COALESCE(wp.views, 0) AS views
  FROM wikidata_cities wd
  JOIN wikidata_city_sitelinks sl USING PLACE
  LEFT JOIN wikipedia_pageviews wp ON wp.article = sl.enwiki_article
  WHERE wd.population > 1000000
  ORDER BY views ASC, wd.population DESC LIMIT 20;

EOF
