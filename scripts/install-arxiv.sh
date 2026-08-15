#!/usr/bin/env bash
# arXiv metadata via OAI-PMH. Public, no auth, rate-limited to ~1 req/sec.
# Default pulls one week of new papers; scale up via HITORRO_ARXIV_FROM /
# HITORRO_ARXIV_UNTIL. arXiv holds ~2.5M papers total across all fields.
#
# Tunables:
#   HITORRO_ARXIV_FROM=2024-01-01     # ISO date; defaults to 7 days ago
#   HITORRO_ARXIV_UNTIL=              # defaults to today
#   HITORRO_ARXIV_SET=                # optional OAI set spec (e.g. cs, physics:hep-th)
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

if [[ "$OSTYPE" == "darwin"* ]]; then
    DEFAULT_FROM=$(date -u -v-7d +%Y-%m-%d)
else
    DEFAULT_FROM=$(date -u -d "-7 days" +%Y-%m-%d)
fi
DEFAULT_UNTIL=$(date -u +%Y-%m-%d)

: "${HITORRO_ARXIV_FROM:=$DEFAULT_FROM}"
: "${HITORRO_ARXIV_UNTIL:=$DEFAULT_UNTIL}"
: "${HITORRO_ARXIV_SET:=}"

DATASET_ID="arxiv"
INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd curl
require_cmd python3

info "arXiv OAI-PMH from=${HITORRO_ARXIV_FROM} until=${HITORRO_ARXIV_UNTIL} set=${HITORRO_ARXIV_SET:-(all)}"

# OAI-PMH ListRecords with cursor (resumptionToken)
url="https://export.arxiv.org/oai2?verb=ListRecords&metadataPrefix=arXiv&from=${HITORRO_ARXIV_FROM}&until=${HITORRO_ARXIV_UNTIL}"
[[ -n "$HITORRO_ARXIV_SET" ]] && url="${url}&set=${HITORRO_ARXIV_SET}"

page=0
: > "$RAW_DIR/all.xml"
resumption=""
while :; do
    page=$((page+1))
    raw="$RAW_DIR/page${page}.xml"
    if [[ -n "$resumption" ]]; then
        info "page $page (resume=${resumption:0:20}...)"
        curl -sSf --get "https://export.arxiv.org/oai2" \
            --data-urlencode "verb=ListRecords" \
            --data-urlencode "resumptionToken=${resumption}" \
            -o "$raw"
    else
        info "page $page (initial)"
        curl -sSf "$url" -o "$raw"
    fi
    resumption=$(python3 -c "
import sys, re
data = open('$raw').read()
m = re.search(r'<resumptionToken[^>]*>([^<]*)</resumptionToken>', data)
print(m.group(1) if m and m.group(1).strip() else '')")
    if [[ -z "$resumption" ]]; then break; fi
    sleep 3   # OAI-PMH courtesy delay
done

info "XML → NDJSON"
python3 -c "
import sys, json, glob, re, html
from xml.etree import ElementTree as ET

# OAI-PMH + arXiv-schema namespaces
NS = {
    'oai':   'http://www.openarchives.org/OAI/2.0/',
    'arxiv': 'http://arxiv.org/OAI/arXiv/'
}

def text(e, tag):
    child = e.find(tag, NS)
    return (child.text or '').strip() if child is not None and child.text else None

with open('$DATA_DIR/arxiv.ndjson', 'w') as out:
    for path in sorted(glob.glob('$RAW_DIR/page*.xml')):
        tree = ET.parse(path)
        for record in tree.iterfind('.//oai:record', NS):
            md = record.find('.//arxiv:arXiv', NS)
            if md is None: continue
            row = {
                'arxiv_id':       text(md, 'arxiv:id'),
                'title':          re.sub(r'\s+', ' ', text(md, 'arxiv:title') or ''),
                'abstract':       re.sub(r'\s+', ' ', text(md, 'arxiv:abstract') or ''),
                'authors':        [(a.find('arxiv:keyname', NS).text or '') + ' ' + (a.find('arxiv:forenames', NS).text or '') if a.find('arxiv:keyname', NS) is not None else ''
                                   for a in md.findall('arxiv:authors/arxiv:author', NS)],
                'categories':     (text(md, 'arxiv:categories') or '').split(),
                'primary_category': (text(md, 'arxiv:categories') or '').split()[0] if text(md, 'arxiv:categories') else None,
                'created':        text(md, 'arxiv:created'),
                'updated':        text(md, 'arxiv:updated'),
                'doi':            text(md, 'arxiv:doi'),
                'journal_ref':    text(md, 'arxiv:journal-ref'),
                'license':        text(md, 'arxiv:license'),
            }
            out.write(json.dumps(row) + '\n')
"

finalize_ndjson "$DATA_DIR/arxiv.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/arxiv.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/arxiv.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID  from=${HITORRO_ARXIV_FROM} until=${HITORRO_ARXIV_UNTIL}")

Try:
  SELECT primary_category, COUNT(*) AS n
  FROM arxiv GROUP BY primary_category ORDER BY n DESC LIMIT 20;

Pull a full month of cs.CL:
  HITORRO_ARXIV_FROM=2025-01-01 HITORRO_ARXIV_UNTIL=2025-01-31 \\
    HITORRO_ARXIV_SET=cs ./scripts/install-arxiv.sh

EOF
