#!/usr/bin/env bash
# World Bank latest-year snapshot of eight development indicators per country.
# Each indicator = one API call; the last-N-years window in the URL lets the
# server return everything up to the most recent that has data, and jq picks
# the newest non-null per country. About 8 HTTP round-trips at install time,
# nothing at query time.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="worldbank-indicators"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

# Indicator code → wide column name. Deliberately picked so every country
# has SOMETHING for most of these; sparse indicators (literacy, R&D spend)
# left out of MVP.
INDICATORS=(
    "NY.GDP.MKTP.CD:gdp_usd"
    "NY.GDP.PCAP.CD:gdp_per_capita_usd"
    "SP.POP.TOTL:population"
    "SP.DYN.LE00.IN:life_expectancy_years"
    "SP.URB.TOTL.IN.ZS:urban_population_pct"
    "SL.UEM.TOTL.ZS:unemployment_pct"
    "EG.ELC.ACCS.ZS:electricity_access_pct"
)

# 5-year lookback lets us get data for countries whose latest report is 1-3
# years old (unemployment/literacy tend to lag). jq picks the most recent
# non-null per country downstream.
YEAR_START=$(($(date +%Y) - 5))
YEAR_END=$(date +%Y)

merged="$DATA_DIR/countries.ndjson"
: > "$merged"

for spec in "${INDICATORS[@]}"; do
    code="${spec%%:*}"
    colname="${spec##*:}"
    raw="$RAW_DIR/${code}.json"
    if [[ ! -f "$raw" || -n "${HITORRO_DATASETS_FORCE:-}" ]]; then
        info "fetching $code..."
        curl -sSf \
            "https://api.worldbank.org/v2/country/all/indicator/${code}?format=json&per_page=20000&date=${YEAR_START}:${YEAR_END}" \
            -o "$raw.part"
        mv "$raw.part" "$raw"
    else
        info "cached: $raw"
    fi
done

info "merging 8 indicators → wide NDJSON (jq)"
# Strategy:
#   1. For each indicator file, project one row per country carrying
#      {iso_a3, name, <colname>, year} using only the latest non-null.
#   2. Concatenate all indicator projections, group by iso_a3, deep-merge
#      via reduce (. * $r) so all columns land on one row per country.
#
# WB rows arrive newest-first per country + indicator, so `first` on
# non-null value gives the most recent report. Also emits a top-level
# `year` per indicator; the final row keeps whichever indicator was
# merged last — good enough for the MVP snapshot (per-cell year would
# take a very different schema).
#
# WB tags aggregates (regions, income groups) with countryiso3code
# values that share the 3-char shape of real ISO codes ("WLD",
# "OED", "AFE") but with country.id starting "Z" or "X" (WB private
# codes). Filter by known-aggregate prefixes — the real ISO-2 codes
# these aggregates use ("ZH", "XM", ...) don't overlap with any
# assigned ISO-3166 country code.
:> "$RAW_DIR/all_parts.ndjson"
for spec in "${INDICATORS[@]}"; do
    code="${spec%%:*}"
    colname="${spec##*:}"
    jq -c --arg col "$colname" '
        (.[1] // [])
        | group_by(.countryiso3code)
        | map(
            # Latest non-null per country, preserving iso_a3 + name.
            [ .[] | select(.value != null) ]
            | sort_by(.date) | reverse | .[0]
            | select(. != null)
            # Real countries only. WB does not tag aggregates as such in
            # the value response — regions ("EAP", "MNA"), income groups
            # ("HIC", "LIC"), and organisational rollups ("OED", "EMU")
            # look identical to ISO alpha-3 country codes. Two-step filter:
            #   1) country.id (WB 2-char code) starts with a letter A-Y
            #      but not X or Z (WB-private code prefixes for digits +
            #      user-assigned codes catch the "1W", "8S" style).
            #   2) countryiso3code is not in an explicit blocklist of
            #      well-known aggregate rollup codes.
            | select(
                (.country.id | test("^[A-W]|^Y")) and
                (.country.id | test("^[XZ]") | not)
              )
            | select(
                (.countryiso3code // "") as $c
                | ["ARB","CEB","CSS","EAP","EAR","EAS","ECA","ECS","EMU","EUU",
                   "FCS","HIC","HPC","IBB","IBD","IBT","IDA","IDB","IDX",
                   "LAC","LCN","LDC","LIC","LMC","LMY","LTE","MEA","MIC","MNA",
                   "NAC","OED","OSS","PRE","PSS","PST","SAS","SSA","SSF","SST",
                   "TEA","TEC","TLA","TMN","TSA","TSS","UMC","WLD","AFE","AFW"]
                | contains([$c]) | not
              )
            | {
                iso_a3: .countryiso3code,
                name:   .country.value,
                year:   (.date | tonumber),
                ($col): .value
              }
          )
        | .[]
    ' "$RAW_DIR/${code}.json" >> "$RAW_DIR/all_parts.ndjson"
done

jq -sc '
    group_by(.iso_a3)
    | map(
        # Deep-merge every partial row for this iso3 into one wide row.
        reduce .[] as $r ({}; . * $r)
      )
    | .[]
    | select(.iso_a3 != null and .iso_a3 != "")
' "$RAW_DIR/all_parts.ndjson" > "$merged"

finalize_ndjson "$merged" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/worldbank_indicators.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/worldbank-indicators.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/{NY.GDP.MKTP.CD,...}.json    per-indicator raw responses
    data/countries.ndjson            wide-column merged
    types/worldbank_indicators.json

Some queries to try in the Datasets tab:

  # Top 10 economies by GDP
  SELECT name, gdp_usd, gdp_per_capita_usd, population
  FROM worldbank_indicators
  WHERE gdp_usd IS NOT NULL
  ORDER BY gdp_usd DESC LIMIT 10;

  # Life expectancy vs GDP per capita — the classic
  SELECT name, gdp_per_capita_usd, life_expectancy_years
  FROM worldbank_indicators
  WHERE gdp_per_capita_usd IS NOT NULL AND life_expectancy_years IS NOT NULL
  ORDER BY gdp_per_capita_usd DESC LIMIT 30;

  # Cities in high-electricity-access countries — semantic join, one hop
  SELECT wd.name AS city, wd.population, wb.electricity_access_pct
  FROM wikidata_cities wd
  JOIN wikidata_countries wc USING PLACE
  JOIN worldbank_indicators wb USING PLACE
  WHERE wb.electricity_access_pct > 99
    AND wd.population > 1000000
  ORDER BY wd.population DESC LIMIT 20;

EOF
