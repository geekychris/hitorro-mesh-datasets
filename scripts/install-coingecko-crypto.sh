#!/usr/bin/env bash
# CoinGecko — top-100 cryptocurrencies by market cap.
# Free API, no auth, soft rate-limited. One HTTP call.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="coingecko-crypto"
URL="https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq

fetch "$URL" "$RAW_DIR/coins.json"
info "sha256: $(sha256_of "$RAW_DIR/coins.json")"

info "json → ndjson (jq)"
# The API's response is a top-level array. Field names are CoinGecko's;
# nullable columns preserved as nulls (some altcoins lack total_supply /
# max_supply / ath).
jq -c '.[] | {
    coin_id:              .id,
    symbol:               .symbol,
    name:                 .name,
    current_price_usd:    .current_price,
    market_cap_usd:       .market_cap,
    market_cap_rank:      .market_cap_rank,
    price_change_24h_pct: .price_change_percentage_24h,
    total_volume_usd:     .total_volume,
    circulating_supply:   .circulating_supply,
    total_supply:         .total_supply,
    max_supply:           .max_supply,
    ath_usd:              .ath,
    ath_date:             .ath_date,
    last_updated:         .last_updated
}' "$RAW_DIR/coins.json" > "$DATA_DIR/coins.ndjson"

finalize_ndjson "$DATA_DIR/coins.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/coingecko_crypto.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/coingecko-crypto.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Top 10 by market cap
  SELECT market_cap_rank, name, symbol, current_price_usd, market_cap_usd
  FROM coingecko_crypto
  ORDER BY market_cap_rank ASC LIMIT 10;

  # Biggest 24h gainers
  SELECT name, symbol, price_change_24h_pct, market_cap_rank
  FROM coingecko_crypto
  WHERE price_change_24h_pct IS NOT NULL
  ORDER BY price_change_24h_pct DESC LIMIT 15;

  # How far off all-time-highs?
  SELECT name, symbol, current_price_usd, ath_usd,
         ((current_price_usd - ath_usd) / ath_usd * 100) AS pct_from_ath
  FROM coingecko_crypto
  WHERE ath_usd IS NOT NULL
  ORDER BY pct_from_ath ASC LIMIT 15;

Refresh with:  HITORRO_DATASETS_FORCE=1 ./scripts/install-coingecko-crypto.sh

EOF
