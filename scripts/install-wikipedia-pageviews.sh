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

# Tunables — override at install to pull more days:
#   HITORRO_WIKIPEDIA_PAGEVIEWS_DAYS=30       # how many recent days to pull
#   HITORRO_WIKIPEDIA_PAGEVIEWS_START_OFFSET=1 # start N days back (Wikimedia lags 24-48h)
#   HITORRO_WIKIPEDIA_PAGEVIEWS_PROJECT=en.wikipedia # or de.wikipedia, all-projects, etc.
: "${HITORRO_WIKIPEDIA_PAGEVIEWS_DAYS:=1}"
: "${HITORRO_WIKIPEDIA_PAGEVIEWS_START_OFFSET:=1}"
: "${HITORRO_WIKIPEDIA_PAGEVIEWS_PROJECT:=en.wikipedia}"

info "Pulling ${HITORRO_WIKIPEDIA_PAGEVIEWS_DAYS} day(s) starting -${HITORRO_WIKIPEDIA_PAGEVIEWS_START_OFFSET}d for ${HITORRO_WIKIPEDIA_PAGEVIEWS_PROJECT}"

_fetch_day() {
    local offset=$1 dest=$2
    local d d_iso
    if [[ "$OSTYPE" == "darwin"* ]]; then
        d=$(date -u -v-${offset}d +%Y/%m/%d)
        d_iso=$(date -u -v-${offset}d +%Y-%m-%d)
    else
        d=$(date -u -d "-$offset days" +%Y/%m/%d)
        d_iso=$(date -u -d "-$offset days" +%Y-%m-%d)
    fi
    local url="https://wikimedia.org/api/rest_v1/metrics/pageviews/top/${HITORRO_WIKIPEDIA_PAGEVIEWS_PROJECT}/all-access/${d}"
    if [[ -f "$dest" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
        echo "$d_iso"
        return 0
    fi
    if curl -sSf \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        -H "Accept: application/json" \
        "$url" -o "$dest.part" 2>/dev/null; then
        mv "$dest.part" "$dest"
        echo "$d_iso"
        return 0
    fi
    rm -f "$dest.part"
    return 1
}

: > "$DATA_DIR/pageviews.ndjson"
success_days=0
skip_days=0
offset=$HITORRO_WIKIPEDIA_PAGEVIEWS_START_OFFSET
attempted=0
target=$HITORRO_WIKIPEDIA_PAGEVIEWS_DAYS
# Give ourselves a generous ceiling — Wikimedia may skip individual
# days for holidays / outages, and we don't want an off-by-one to
# make a 30-day install run forever.
max_attempts=$((target * 3 + 5))
while [[ $success_days -lt $target && $attempted -lt $max_attempts ]]; do
    dest="$RAW_DIR/top-$(printf %03d $offset).json"
    if d_iso=$(_fetch_day "$offset" "$dest"); then
        info "day -${offset}d → $d_iso"
        jq -c --arg d "$d_iso" '
            .items[0].articles[] | {
                article:     .article,
                title:       (.article | gsub("_"; " ")),
                view_rank:   .rank,
                views:       .views,
                sample_date: $d
            }
        ' "$dest" >> "$DATA_DIR/pageviews.ndjson"
        success_days=$((success_days + 1))
    else
        warn "day -${offset}d not yet published; skipping"
        skip_days=$((skip_days + 1))
    fi
    offset=$((offset + 1))
    attempted=$((attempted + 1))
done

if [[ $success_days -eq 0 ]]; then
    die "Wikimedia API returned no data for any of the last ${max_attempts} days"
fi
info "pulled $success_days day(s), skipped $skip_days"

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
