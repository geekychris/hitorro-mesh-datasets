#!/usr/bin/env bash
# Wikipedia top-1000 pageviews for the most recent complete UTC day.
# The pageview aggregation lags ~24-48h — the loop tries yesterday, then
# two days ago, etc, up to 5 days back.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="wikipedia-pageviews"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

# Try recent days until one returns real data. Wikimedia sometimes lags
# a day or two behind on the top-N endpoint.
sample_date=""
for offset in 1 2 3 4 5; do
    if [[ "$OSTYPE" == "darwin"* ]]; then
        d=$(date -u -v-${offset}d +%Y/%m/%d)
        d_iso=$(date -u -v-${offset}d +%Y-%m-%d)
    else
        d=$(date -u -d "-$offset days" +%Y/%m/%d)
        d_iso=$(date -u -d "-$offset days" +%Y-%m-%d)
    fi
    url="https://wikimedia.org/api/rest_v1/metrics/pageviews/top/en.wikipedia/all-access/${d}"
    info "trying $d (offset -${offset}d)"
    if curl -sSf \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        -H "Accept: application/json" \
        "$url" -o "$RAW_DIR/top.json.part" 2>/dev/null; then
        sample_date="$d_iso"
        mv "$RAW_DIR/top.json.part" "$RAW_DIR/top.json"
        break
    fi
    rm -f "$RAW_DIR/top.json.part"
done

if [[ -z "$sample_date" ]]; then
    die "Wikimedia API returned no data for any of the last 5 days"
fi

info "sha256: $(sha256_of "$RAW_DIR/top.json")"

info "json → ndjson (jq) — top 1000 for $sample_date"
# The response has .items[0].articles; each article carries { article,
# views, rank }. We add a display title (underscores → spaces) so the UI
# reads nicely, and stamp sample_date on every row for filtering later
# if this becomes a multi-day dataset.
jq -c --arg d "$sample_date" '
    .items[0].articles[] | {
        article:     .article,
        title:       (.article | gsub("_"; " ")),
        view_rank:   .rank,
        views:       .views,
        sample_date: $d
    }
' "$RAW_DIR/top.json" > "$DATA_DIR/pageviews.ndjson"

finalize_ndjson "$DATA_DIR/pageviews.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/wikipedia_pageviews.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/wikipedia-pageviews.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # What's Wikipedia reading today?
  SELECT view_rank, title, views FROM wikipedia_pageviews ORDER BY view_rank LIMIT 20;

  # Which of today's top articles look like city names Wikidata knows?
  SELECT wp.view_rank, wp.title, wp.views, wd.wikidata_qid, wd.population
  FROM wikipedia_pageviews wp
  JOIN wikidata_cities wd ON wp.title = wd.name
  ORDER BY wp.views DESC LIMIT 30;

  # Country match — how many countries are in the daily top-1000?
  SELECT COUNT(*) AS n
  FROM wikipedia_pageviews wp
  JOIN wikidata_countries wc ON wp.title = wc.name;

Refresh with:  HITORRO_DATASETS_FORCE=1 ./scripts/install-wikipedia-pageviews.sh

EOF
