#!/usr/bin/env bash
# Wikidata cities > 100 000 population.
#
# Uses the public SPARQL endpoint at query.wikidata.org. Wikidata's usage
# policy requires a descriptive User-Agent — see
# https://meta.wikimedia.org/wiki/User-Agent_policy. The query embeds all
# the filters and joins so the whole install is one HTTP round-trip.
#
# The endpoint enforces a 60-second timeout per query. LIMIT + strict
# instance-of Q515 (city) usually keeps us well under that. If the SPARQL
# server is overloaded you'll see a 500 or a partial result — re-run with
# HITORRO_DATASETS_FORCE=1 to bypass the cache.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="wikidata-cities"
SPARQL_ENDPOINT="https://query.wikidata.org/sparql"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

QUERY='SELECT DISTINCT ?item ?itemLabel ?population ?lat ?lon ?geonames ?country ?countryIso WHERE {
  ?item wdt:P31 wd:Q515.
  ?item wdt:P1082 ?population.
  FILTER(?population > 100000)
  ?item p:P625 ?coordStmt.
  ?coordStmt psv:P625 ?coordNode.
  ?coordNode wikibase:geoLatitude ?lat;
             wikibase:geoLongitude ?lon.
  OPTIONAL { ?item wdt:P1566 ?geonames. }
  OPTIONAL {
    ?item wdt:P17 ?country.
    ?country wdt:P297 ?countryIso.
  }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
LIMIT 20000'

if [[ -f "$RAW_DIR/cities.json" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
    info "cached: $RAW_DIR/cities.json — set HITORRO_DATASETS_FORCE=1 to refresh"
else
    info "SPARQL → $SPARQL_ENDPOINT (~15 s)"
    curl -sSf -X POST "$SPARQL_ENDPOINT" \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        -H "Accept: application/sparql-results+json" \
        --data-urlencode "query=$QUERY" \
        -o "$RAW_DIR/cities.json.part"
    mv "$RAW_DIR/cities.json.part" "$RAW_DIR/cities.json"
fi
info "sha256: $(sha256_of "$RAW_DIR/cities.json")"

info "sparql-json → ndjson (jq)"
# OPTIONAL bindings can be absent entirely; the (.x // {}).value guards
# against "cannot index null" and produce explicit nulls to match the
# TSV loader's convention.
jq -c '.results.bindings[] | {
    wikidata_qid: (.item.value | sub("http://www.wikidata.org/entity/"; "")),
    name:         .itemLabel.value,
    population:   (.population.value | tonumber),
    latitude:     (.lat.value | tonumber),
    longitude:    (.lon.value | tonumber),
    geonames_id:  ((.geonames // {}).value // null),
    country_qid:  (if .country then (.country.value | sub("http://www.wikidata.org/entity/"; "")) else null end),
    country_iso:  ((.countryIso // {}).value // null)
  }' "$RAW_DIR/cities.json" > "$DATA_DIR/cities.ndjson"
finalize_ndjson "$DATA_DIR/cities.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/wikidata_cities.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/wikidata-cities.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/cities.json           SPARQL raw response
    data/cities.ndjson
    types/wikidata_cities.json

Small enough to broadcast. Register with a running driver in one shot:

  ./scripts/register-installed.sh

The identity-glue query — every GeoNames city that also has a Wikidata QID,
side-by-side with the Natural Earth country attributes:

  SELECT gn.name, gn.population,
         wd.wikidata_qid, wd.name AS wd_name,
         ne.income_grp, ne.continent
  FROM geonames_cities15000 gn
  JOIN wikidata_cities wd ON gn.geonameid = CAST(wd.geonames_id AS BIGINT)
  JOIN natural_earth_countries ne ON gn.country_code = ne.iso_a2
  WHERE gn.population > 500000
  ORDER BY gn.population DESC LIMIT 20;

Or "cities Wikidata knows but GeoNames doesn't yet" — a data-quality query
that would need three READMEs to write without the identifier glue:

  SELECT wd.wikidata_qid, wd.name, wd.population, wd.country_iso
  FROM wikidata_cities wd
  LEFT JOIN geonames_cities15000 gn
    ON gn.geonameid = CAST(wd.geonames_id AS BIGINT)
  WHERE gn.geonameid IS NULL
  ORDER BY wd.population DESC LIMIT 20;

EOF
