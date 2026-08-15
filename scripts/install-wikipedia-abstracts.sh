#!/usr/bin/env bash
# Wikipedia article abstracts — the introductory paragraph + link
# summary Wikimedia ships as its "abstract" dump. ~1GB gzipped
# (English), streams straight through xmlstarlet to NDJSON.
#
# Tunables:
#   HITORRO_WIKIPEDIA_LANG=en          # dump language (en, de, es, fr, ja, zh, ...)
#   HITORRO_WIKIPEDIA_ABSTRACTS_LIMIT=0 # 0 = no limit; N = take first N articles
#   HITORRO_WIKIPEDIA_DATE=latest      # dump date, e.g. 20250401
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

: "${HITORRO_WIKIPEDIA_LANG:=en}"
: "${HITORRO_WIKIPEDIA_ABSTRACTS_LIMIT:=0}"
: "${HITORRO_WIKIPEDIA_DATE:=latest}"

DATASET_ID="wikipedia-abstracts"
INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

if [[ -z "${HITORRO_WIKIPEDIA_CONFIRM:-}" && ! -f "$RAW_DIR/${HITORRO_WIKIPEDIA_LANG}wiki-abstract.xml.gz" ]]; then
    warn "Wikipedia abstracts dump for ${HITORRO_WIKIPEDIA_LANG}wiki is ~1GB gzipped, ~5GB uncompressed."
    warn "Rerun with HITORRO_WIKIPEDIA_CONFIRM=1 to proceed."
    exit 1
fi

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl
require_cmd gunzip

URL="https://dumps.wikimedia.org/${HITORRO_WIKIPEDIA_LANG}wiki/${HITORRO_WIKIPEDIA_DATE}/${HITORRO_WIKIPEDIA_LANG}wiki-${HITORRO_WIKIPEDIA_DATE}-abstract.xml.gz"
DEST="$RAW_DIR/${HITORRO_WIKIPEDIA_LANG}wiki-abstract.xml.gz"
fetch "$URL" "$DEST"
info "sha256: $(sha256_of "$DEST")"

# The abstract dump is XML: <feed><doc><title>...</title><url>...</url>
# <abstract>...</abstract><links><sublink linktype="nav"><anchor>...</anchor>
# <link>...</link></sublink>...</links></doc>...</feed>
#
# We stream via awk on the gzip stream — fast, no XML parser needed.
info "xml.gz → ndjson (streaming; may take 5-15 min for a full lang)"

gunzip -c "$DEST" | python3 -c '
import sys, json, re, os

limit = int(os.environ.get("HITORRO_WIKIPEDIA_ABSTRACTS_LIMIT", "0"))

buf = []
in_doc = False
count = 0

def flush():
    global count
    doc = "".join(buf)
    title    = re.search(r"<title>(.*?)</title>", doc, re.DOTALL)
    url      = re.search(r"<url>(.*?)</url>", doc, re.DOTALL)
    abstract = re.search(r"<abstract>(.*?)</abstract>", doc, re.DOTALL)
    if title and url:
        sys.stdout.write(json.dumps({
            "title":    (title.group(1) or "").replace("Wikipedia: ", "").strip(),
            "url":      (url.group(1) or "").strip(),
            "abstract": (abstract.group(1) if abstract else "").strip(),
            "lang":     os.environ.get("HITORRO_WIKIPEDIA_LANG", "en")
        }) + "\n")
        count += 1
        if limit and count >= limit:
            sys.exit(0)

for line in sys.stdin:
    if "<doc>" in line:
        in_doc = True
        buf = [line]
        continue
    if in_doc:
        buf.append(line)
    if "</doc>" in line and in_doc:
        flush()
        buf = []
        in_doc = False
' > "$DATA_DIR/abstracts.ndjson"

finalize_ndjson "$DATA_DIR/abstracts.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/wikipedia_abstracts.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/wikipedia-abstracts.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID  lang=${HITORRO_WIKIPEDIA_LANG}")

Try:
  SELECT title, LENGTH(abstract) AS abstract_len
  FROM wikipedia_abstracts WHERE abstract IS NOT NULL
  ORDER BY abstract_len DESC LIMIT 20;

EOF
