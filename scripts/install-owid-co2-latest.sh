#!/usr/bin/env bash
# Our World in Data — CO2/energy latest-year snapshot per country.
# One CSV download, awk-parsed into per-country latest-year NDJSON.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="owid-co2-latest"
URL="https://github.com/owid/co2-data/raw/master/owid-co2-data.csv"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd awk

fetch "$URL" "$RAW_DIR/owid-co2-data.csv"
info "sha256: $(sha256_of "$RAW_DIR/owid-co2-data.csv")"

# The OWID CSV has ~80 columns. We only care about 12 of them; the header
# tells us where each lives so we don't hardcode positions. gawk-only
# features are avoided so it runs on mac awk too.
info "csv → ndjson (awk)"
awk -F',' '
    NR == 1 {
        # Header row — index the columns we want.
        for (i = 1; i <= NF; i++) {
            gsub(/^"|"$/, "", $i)
            col[$i] = i
        }
        # Validate we got the essentials.
        for (want in wanted) delete wanted[want]
        # Column list validated against the shipped OWID CSV — the renewables
        # % column was retired in the 2024 refresh; we compute a rough
        # "energy_intensity" using energy_per_gdp instead, or just skip
        # renewables entirely for the MVP.
        split("iso_code country year population gdp co2 co2_per_capita coal_co2 oil_co2 gas_co2 primary_energy_consumption energy_per_capita share_global_co2", names, " ")
        for (i in names) wanted[names[i]] = 1
        for (n in wanted) if (!(n in col)) {
            print "missing column: " n > "/dev/stderr"; exit 2
        }
        next
    }
    # Rows without an iso_code are OWID rollups (World, Africa, EU-27,
    # income groups). Skip them so joins to country tables are clean.
    {
        iso = $col["iso_code"]
        gsub(/^"|"$/, "", iso)
        if (iso == "" || length(iso) != 3) next

        year = $col["year"] + 0
        co2  = $col["co2"]
        # Track (per country) the row with the latest year that has a real
        # co2 value — that becomes the snapshot.
        if (co2 != "" && year > best_year[iso]) {
            best_year[iso] = year
            for (n in wanted) best_val[iso, n] = $col[n]
        }
    }
    END {
        for (iso in best_year) {
            country = best_val[iso, "country"]; gsub(/^"|"$/, "", country); gsub(/"/, "\\\"", country)
            printf "{\"iso_a3\":\"%s\",\"country\":\"%s\",\"year\":%d",
                iso, country, best_year[iso]

            # numeric fields — emit null for empty CSV cells
            for (n in num_map) delete num_map[n]
            split("population:population:long gdp:gdp:double co2:co2_mt:double co2_per_capita:co2_per_capita_t:double coal_co2:coal_co2_mt:double oil_co2:oil_co2_mt:double gas_co2:gas_co2_mt:double primary_energy_consumption:primary_energy_consumption_twh:double energy_per_capita:energy_per_capita_kwh:double share_global_co2:share_global_co2_pct:double",
                  spec, " ")
            for (i in spec) {
                n = spec[i]
                split(n, parts, ":")
                src = parts[1]; dst = parts[2]
                v = best_val[iso, src]
                gsub(/^"|"$/, "", v)
                if (v == "") { printf ",\"%s\":null", dst }
                else         { printf ",\"%s\":%s", dst, v }
            }
            print "}"
        }
    }
' "$RAW_DIR/owid-co2-data.csv" > "$DATA_DIR/countries.ndjson"

finalize_ndjson "$DATA_DIR/countries.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/owid_co2_latest.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/owid-co2-latest.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:

  SELECT country, co2_mt, co2_per_capita_t, share_global_co2_pct
  FROM owid_co2_latest
  WHERE share_global_co2_pct IS NOT NULL
  ORDER BY co2_mt DESC LIMIT 10;

  # Rich but low-carbon — GDP per capita vs CO2 per capita, semantic join
  SELECT ow.country, wb.gdp_per_capita_usd, ow.co2_per_capita_t
  FROM owid_co2_latest ow
  JOIN worldbank_indicators wb USING PLACE
  WHERE wb.gdp_per_capita_usd > 40000 AND ow.co2_per_capita_t IS NOT NULL
  ORDER BY ow.co2_per_capita_t ASC LIMIT 20;

EOF
