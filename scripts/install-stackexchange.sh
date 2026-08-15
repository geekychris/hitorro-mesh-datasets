#!/usr/bin/env bash
# Stack Exchange data dump for a single site — questions + answers +
# users + tags as parsed NDJSON.
#
# The dump is a 7z archive of XML files per site. Small SE sites
# (~10-100MB) install quickly; StackOverflow itself is ~90GB compressed.
#
# Tunables:
#   HITORRO_STACKEXCHANGE_SITE=3dprinting  # any SE site slug (see https://stackexchange.com/sites)
#   HITORRO_STACKEXCHANGE_TABLE=posts      # posts | users | tags | comments | votes | badges
#
# Popular small choices: datascience, 3dprinting, tex, physics.stackexchange
# Popular medium: math.stackexchange, superuser
# WARNING: stackoverflow is ~90GB compressed — rerun with confirm flag.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

: "${HITORRO_STACKEXCHANGE_SITE:=3dprinting}"
: "${HITORRO_STACKEXCHANGE_TABLE:=posts}"

DATASET_ID="stackexchange-${HITORRO_STACKEXCHANGE_SITE}-${HITORRO_STACKEXCHANGE_TABLE}"
INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

if [[ "$HITORRO_STACKEXCHANGE_SITE" == "stackoverflow" && -z "${HITORRO_STACKEXCHANGE_CONFIRM:-}" ]]; then
    warn "stackoverflow dump is ~90GB compressed, ~200GB uncompressed."
    warn "Rerun with HITORRO_STACKEXCHANGE_CONFIRM=1 to proceed."
    exit 1
fi

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd curl
require_cmd 7z
require_cmd python3

# Some sites use suffixed archives (site.com.7z), some just site.7z. Try both.
BASE="https://archive.org/download/stackexchange"
for candidate in "${HITORRO_STACKEXCHANGE_SITE}.7z" "${HITORRO_STACKEXCHANGE_SITE}.stackexchange.com.7z"; do
    URL="${BASE}/${candidate}"
    DEST="$RAW_DIR/${candidate}"
    if curl -sSfLI "$URL" -o /dev/null 2>/dev/null; then
        fetch "$URL" "$DEST"
        ARCHIVE="$DEST"
        break
    fi
done
[[ -n "${ARCHIVE:-}" ]] || die "Cannot find Stack Exchange archive for site '${HITORRO_STACKEXCHANGE_SITE}' — check https://archive.org/details/stackexchange for the exact filename"

info "sha256: $(sha256_of "$ARCHIVE")"

info "extract ${HITORRO_STACKEXCHANGE_TABLE^}.xml from 7z"
case "$HITORRO_STACKEXCHANGE_TABLE" in
    posts)    XML="Posts.xml" ;;
    users)    XML="Users.xml" ;;
    tags)     XML="Tags.xml" ;;
    comments) XML="Comments.xml" ;;
    votes)    XML="Votes.xml" ;;
    badges)   XML="Badges.xml" ;;
    *) die "unknown table: $HITORRO_STACKEXCHANGE_TABLE" ;;
esac
7z x -y -o"$RAW_DIR" "$ARCHIVE" "$XML" > /dev/null

info "XML → NDJSON"
python3 -c "
import sys, json, xml.etree.ElementTree as ET

for _, elem in ET.iterparse('$RAW_DIR/$XML', events=('end',)):
    if elem.tag != 'row': continue
    row = dict(elem.attrib)
    # SE encodes ints as strings; leave them — the JVS type system
    # coerces core_long fields at index time.
    sys.stdout.write(json.dumps(row) + '\n')
    elem.clear()
" > "$DATA_DIR/${HITORRO_STACKEXCHANGE_TABLE}.ndjson"

finalize_ndjson "$DATA_DIR/${HITORRO_STACKEXCHANGE_TABLE}.ndjson" > /dev/null

# Type is dynamic — SE row attributes vary per site version. Ship a
# permissive type that lists the commonly-present fields; extra fields
# in the row are silently accepted by JVSLuceneIndexWriter.
cp "$MODULE_ROOT/src/main/resources/types/stackexchange_${HITORRO_STACKEXCHANGE_TABLE}.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/stackexchange.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try:
  # Recent posts (adjust for your site)
  SELECT Id, Title, Score FROM stackexchange_${HITORRO_STACKEXCHANGE_SITE}_${HITORRO_STACKEXCHANGE_TABLE}
  ORDER BY Score DESC LIMIT 20;

Different site:
  HITORRO_STACKEXCHANGE_SITE=datascience ./scripts/install-stackexchange.sh

EOF
