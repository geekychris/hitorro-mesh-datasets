#!/usr/bin/env bash
# Wikidata sovereign states with all major country identifier cross-refs.
# Small (~200 rows), one SPARQL call.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="wikidata-countries"
SPARQL_ENDPOINT="https://query.wikidata.org/sparql"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

# Wikidata property IDs:
#   P297  ISO 3166-1 alpha-2
#   P298  ISO 3166-1 alpha-3
#   P299  ISO 3166-1 numeric
#   P901  FIPS 10-4 (countries and regions)
#   P2082 UN M.49
#   P30   continent
#   P1082 population (latest)
#
# We take sovereign states (Q3624078) OR country (Q6256) with instance-of
# subclass reasoning stripped — the plain-vanilla P31 constraint keeps the
# result set to ~200 rows and fits inside the 60s query timeout. Population
# and continent are optional so entries without them still show up.
QUERY='SELECT DISTINCT ?item ?itemLabel ?iso_a2 ?iso_a3 ?iso_num ?fips ?un_m49 ?continentLabel ?population WHERE {
  { ?item wdt:P31 wd:Q3624078. } UNION { ?item wdt:P31 wd:Q6256. }
  OPTIONAL { ?item wdt:P297  ?iso_a2. }
  OPTIONAL { ?item wdt:P298  ?iso_a3. }
  OPTIONAL { ?item wdt:P299  ?iso_num. }
  OPTIONAL { ?item wdt:P901  ?fips. }
  OPTIONAL { ?item wdt:P2082 ?un_m49. }
  OPTIONAL { ?item wdt:P30   ?continent. }
  OPTIONAL { ?item wdt:P1082 ?population. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}'

if [[ -f "$RAW_DIR/countries.json" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
    info "cached: $RAW_DIR/countries.json — set HITORRO_DATASETS_FORCE=1 to refresh"
else
    info "SPARQL → $SPARQL_ENDPOINT"
    curl -sSf -X POST "$SPARQL_ENDPOINT" \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        -H "Accept: application/sparql-results+json" \
        --data-urlencode "query=$QUERY" \
        -o "$RAW_DIR/countries.json.part"
    mv "$RAW_DIR/countries.json.part" "$RAW_DIR/countries.json"
fi
info "sha256: $(sha256_of "$RAW_DIR/countries.json")"

info "sparql-json → ndjson (jq)"
# Every OPTIONAL binding may or may not appear per row. (.x // {}).value
# guards the missing case. Duplicate items (same QID appearing multiple
# times due to multiple population statements, say) are unified in a
# post-pass — jq's group_by handles that.
jq -c '
  [.results.bindings[] | {
      wikidata_qid: (.item.value | sub("http://www.wikidata.org/entity/"; "")),
      name:         .itemLabel.value,
      iso_a2:       ((.iso_a2  // {}).value // null),
      iso_a3:       ((.iso_a3  // {}).value // null),
      iso_num:      ((.iso_num // {}).value // null),
      fips:         ((.fips    // {}).value // null),
      un_m49:       ((.un_m49  // {}).value // null),
      continent:    ((.continentLabel // {}).value // null),
      population:   (if .population then (.population.value | tonumber) else null end)
  }]
  | group_by(.wikidata_qid)
  | map(reduce .[] as $r ({}; . * $r))
  | .[]
' "$RAW_DIR/countries.json" > "$DATA_DIR/countries.ndjson"
ok "wrote $(wc -l < "$DATA_DIR/countries.ndjson") records to $DATA_DIR/countries.ndjson"

cp "$MODULE_ROOT/src/main/resources/types/wikidata_countries.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/wikidata-countries.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/countries.json         SPARQL raw response
    data/countries.ndjson
    types/wikidata_countries.json

Register via the driver auto-registration:

  ./scripts/register-installed.sh

Because this dataset carries every widely-used country id, joins that would
otherwise require two hops now take one. E.g. "NOAA stations enriched with
their Natural Earth country attributes" — before, needed geonames-country-info
in the middle (fips → iso_a2). Now:

  SELECT s.name, ne.name AS country, ne.income_grp
  FROM noaa_ghcnd_stations s
  JOIN wikidata_countries wc ON s.fips_country = wc.fips
  JOIN natural_earth_countries ne ON wc.iso_a2 = ne.iso_a2
  WHERE s.elevation > 5000;

Or via the semantic pass:

  SELECT s.name, ne.name AS country, ne.income_grp
  FROM noaa_ghcnd_stations s
  JOIN wikidata_countries wc USING PLACE
  JOIN natural_earth_countries ne USING PLACE
  WHERE s.elevation > 5000;

EOF
