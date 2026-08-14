#!/usr/bin/env bash
# NOAA GHCN-Daily station inventory — ~130 000 rows, ~10 MB.
#
# GHCN-D publishes ghcnd-stations.txt as a fixed-width record per station.
# Column layout (from ghcnd-readme.txt):
#
#   ID           1-11    Character
#   LATITUDE    13-20    Real
#   LONGITUDE   22-30    Real
#   ELEVATION   32-37    Real     (-999.9 = missing)
#   STATE       39-40    Character
#   NAME        42-71    Character
#   GSNFLAG     73-75    Character
#   HCNCRNFLAG  77-79    Character
#   WMOID       81-85    Character
#
# The first two chars of ID are the FIPS 10-4 country code — we emit
# them as a derived `fips_country` column so USING PLACE joins to
# geonames-country-info work through the shared id.fips role.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="noaa-ghcnd-stations"
URL="https://www.ncei.noaa.gov/pub/data/ghcn/daily/ghcnd-stations.txt"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd awk

fetch "$URL" "$RAW_DIR/ghcnd-stations.txt"
info "sha256: $(sha256_of "$RAW_DIR/ghcnd-stations.txt")"

info "fixed-width → ndjson (awk)"
# awk's substr indexing is 1-based; second arg is length, not end.
# Trailing whitespace is stripped from each field. Elevation -999.9 means
# unknown per the GHCN readme — we preserve it verbatim; downstream SQL
# can filter WHERE elevation > -999.
awk 'BEGIN { OFS="" }
    length($0) < 41 { next }
    {
        id      = substr($0,  1, 11); sub(/[ \t]+$/, "", id)
        lat     = substr($0, 13,  8); gsub(/^ +| +$/, "", lat)
        lon     = substr($0, 22,  9); gsub(/^ +| +$/, "", lon)
        elev    = substr($0, 32,  6); gsub(/^ +| +$/, "", elev)
        state   = substr($0, 39,  2); sub(/[ \t]+$/, "", state)
        name    = substr($0, 42, 30); sub(/[ \t]+$/, "", name)
        gsn     = substr($0, 73,  3); sub(/[ \t]+$/, "", gsn)
        hcncrn  = substr($0, 77,  3); sub(/[ \t]+$/, "", hcncrn)
        wmoid   = substr($0, 81,  5); sub(/[ \t]+$/, "", wmoid)
        fips    = substr(id, 1, 2)

        # Escape any embedded quotes / backslashes in string fields.
        gsub(/\\/, "\\\\", name); gsub(/"/, "\\\"", name)

        printf "{\"station_id\":\"%s\",\"fips_country\":\"%s\",\"latitude\":%s,\"longitude\":%s,\"elevation\":%s,\"state\":",
               id, fips, (lat == "" ? "null" : lat), (lon == "" ? "null" : lon), (elev == "" ? "null" : elev)
        printf (state == "" ? "null" : "\"" state "\"")
        printf ",\"name\":\"%s\",\"gsn_flag\":", name
        printf (gsn == "" ? "null" : "\"" gsn "\"")
        printf ",\"hcn_crn_flag\":"
        printf (hcncrn == "" ? "null" : "\"" hcncrn "\"")
        printf ",\"wmo_id\":"
        printf (wmoid == "" ? "null" : "\"" wmoid "\"")
        print  "}"
    }
' "$RAW_DIR/ghcnd-stations.txt" > "$DATA_DIR/stations.ndjson"
ok "wrote $(wc -l < "$DATA_DIR/stations.ndjson") records to $DATA_DIR/stations.ndjson"

cp "$MODULE_ROOT/src/main/resources/types/noaa_ghcnd_stations.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/noaa-ghcnd-stations.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Install layout:
  $INSTALL_DIR/
    manifest.yaml
    raw/ghcnd-stations.txt
    data/stations.ndjson
    types/noaa_ghcnd_stations.json

Register with the driver (auto if hitorro-mesh-datasets is on its classpath):

  ./scripts/register-installed.sh

Two showcase queries:

  # 1. Every US weather station reporting to GCOS, with the country info
  #    joined via the derived fips_country → id.fips role match.
  SELECT s.station_id, s.name, s.latitude, s.longitude, s.elevation, ci.country
  FROM noaa_ghcnd_stations s
  JOIN geonames_country_info ci USING PLACE
  WHERE s.gsn_flag = 'GSN' AND s.fips_country = 'US'
  ORDER BY s.elevation DESC LIMIT 20;

  # 2. Highest-elevation stations per continent — Wikidata cities in the
  #    same country give you the "nearest big city" context, three-way join.
  SELECT s.name AS station, s.elevation, ci.country, ci.continent
  FROM noaa_ghcnd_stations s
  JOIN geonames_country_info ci USING PLACE
  WHERE s.elevation > 4000
  ORDER BY s.elevation DESC LIMIT 30;

EOF
