#!/usr/bin/env bash
# GeoNames allCountries — every populated place, admin division, and
# feature GeoNames knows about. ~12 million rows.
#
# WARNING: ~350MB download, ~1.5GB uncompressed TSV, ~600MB NDJSON.bz2.
# Streams through the same tsv_to_ndjson path — takes ~5 minutes.
#
# Set HITORRO_DATASETS_FORCE=1 to re-download.
# Set HITORRO_GEONAMES_ALLCOUNTRIES_CONFIRM=1 to bypass the size warning.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="geonames-allcountries"
URL="https://download.geonames.org/export/dump/allCountries.zip"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

if [[ -z "${HITORRO_GEONAMES_ALLCOUNTRIES_CONFIRM:-}" && ! -f "$RAW_DIR/allCountries.zip" ]]; then
    warn "This will download ~350MB and produce ~600MB of NDJSON (12M rows)."
    warn "Rerun with HITORRO_GEONAMES_ALLCOUNTRIES_CONFIRM=1 to proceed."
    exit 1
fi

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

fetch "$URL" "$RAW_DIR/allCountries.zip"
info "sha256: $(sha256_of "$RAW_DIR/allCountries.zip")"

require_cmd unzip
info "unzip (~1.5GB uncompressed)"
unzip -qo "$RAW_DIR/allCountries.zip" -d "$RAW_DIR"

COLS="geonameid,name,asciiname,alternatenames,latitude,longitude,feature_class,feature_code,country_code,cc2,admin1_code,admin2_code,admin3_code,admin4_code,population,elevation,dem,timezone,modification_date"
NUMERIC="geonameid,latitude,longitude,population,elevation,dem"

info "TSV → NDJSON (12M rows, ~5 min)"
tsv_to_ndjson "$RAW_DIR/allCountries.txt" "$DATA_DIR/allcountries.ndjson" "$COLS" "$NUMERIC"

cp "$MODULE_ROOT/src/main/resources/types/geonames_allcountries.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/geonames-allcountries.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Feature-class breakdown — how much of GeoNames is admin vs place vs hydro?
  SELECT feature_class, COUNT(*) AS n
  FROM geonames_allcountries
  GROUP BY feature_class ORDER BY n DESC;

  # Places at altitude > 4000m
  SELECT country_code, name, elevation
  FROM geonames_allcountries
  WHERE elevation > 4000 AND feature_class = 'P'
  ORDER BY elevation DESC LIMIT 20;

EOF
