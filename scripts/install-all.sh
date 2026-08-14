#!/usr/bin/env bash
# Install every dataset shipped with this module. Cheap because everything
# is cached under $HITORRO_DATASETS_HOME.
set -euo pipefail
cd "$(dirname "$0")"

for s in install-geonames-country-info.sh install-geonames-cities15000.sh; do
    printf "\n=== %s ===\n" "$s"
    ./"$s"
done

printf "\nAll datasets installed under \$HITORRO_DATASETS_HOME (default ~/.hitorro/datasets).\n"
