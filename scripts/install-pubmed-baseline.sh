#!/usr/bin/env bash
# NIH PubMed annual baseline — ~30M biomedical citations.
# Each baseline file is ~30MB compressed gz XML; there are ~1200 files
# for a full year, totalling ~30GB compressed / ~200GB uncompressed.
#
# WARNING: default settings pull ONE file (~30MB, ~30k records) for a
# quick smoke test. Real ingestion is the full baseline — takes hours.
#
# Tunables:
#   HITORRO_PUBMED_FILES=1                     # how many baseline files to pull
#   HITORRO_PUBMED_START=1                     # first file number (baseline files are numbered pubmed25n0001..)
#   HITORRO_PUBMED_YEAR=25                     # baseline year suffix
#   HITORRO_PUBMED_CONFIRM=1                   # required for FILES > 50
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

: "${HITORRO_PUBMED_FILES:=1}"
: "${HITORRO_PUBMED_START:=1}"
: "${HITORRO_PUBMED_YEAR:=25}"

if [[ "$HITORRO_PUBMED_FILES" -gt 50 && -z "${HITORRO_PUBMED_CONFIRM:-}" ]]; then
    warn "Requested ${HITORRO_PUBMED_FILES} baseline files (~${HITORRO_PUBMED_FILES}0 MB compressed, ~${HITORRO_PUBMED_FILES}00 MB uncompressed)."
    warn "Rerun with HITORRO_PUBMED_CONFIRM=1 to proceed."
    exit 1
fi

DATASET_ID="pubmed-baseline"
INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd curl
require_cmd python3

BASE_URL="https://ftp.ncbi.nlm.nih.gov/pubmed/baseline"
info "Pulling ${HITORRO_PUBMED_FILES} baseline file(s) starting at pubmed${HITORRO_PUBMED_YEAR}n$(printf %04d $HITORRO_PUBMED_START).xml.gz"

end=$((HITORRO_PUBMED_START + HITORRO_PUBMED_FILES - 1))
: > "$DATA_DIR/pubmed.ndjson"
for i in $(seq -f "%04g" $HITORRO_PUBMED_START $end); do
    name="pubmed${HITORRO_PUBMED_YEAR}n${i}.xml.gz"
    fetch "${BASE_URL}/${name}" "$RAW_DIR/${name}"
    info "  parse ${name}"
    gunzip -c "$RAW_DIR/${name}" | python3 -c "
import sys, json, xml.etree.ElementTree as ET
for _, elem in ET.iterparse(sys.stdin.buffer, events=('end',)):
    if elem.tag != 'PubmedArticle': continue
    med = elem.find('MedlineCitation')
    if med is None: elem.clear(); continue
    art = med.find('Article')
    pmid = med.findtext('PMID') or ''
    title = (art.findtext('ArticleTitle') if art is not None else '') or ''
    abstract_parts = []
    if art is not None:
        for a in art.findall('.//AbstractText'):
            if a.text: abstract_parts.append(a.text)
    journal = ''
    if art is not None:
        j = art.find('Journal/Title')
        if j is not None and j.text: journal = j.text
    authors = []
    if art is not None:
        for a in art.findall('AuthorList/Author'):
            ln = a.findtext('LastName') or ''
            fn = a.findtext('ForeName') or ''
            authors.append((fn + ' ' + ln).strip())
    year = None
    if art is not None:
        y = art.find('Journal/JournalIssue/PubDate/Year')
        if y is not None and y.text:
            try: year = int(y.text)
            except: pass
    doi = None
    for aid in med.findall('.//ArticleId'):
        if aid.attrib.get('IdType') == 'doi': doi = aid.text
    keywords = []
    for k in med.findall('KeywordList/Keyword'):
        if k.text: keywords.append(k.text)
    mesh_terms = []
    for m in med.findall('MeshHeadingList/MeshHeading/DescriptorName'):
        if m.text: mesh_terms.append(m.text)
    row = {
        'pmid': pmid, 'title': title, 'abstract': ' '.join(abstract_parts),
        'journal': journal, 'year': year, 'doi': doi,
        'authors': authors, 'keywords': keywords, 'mesh_terms': mesh_terms
    }
    sys.stdout.write(json.dumps(row) + '\n')
    elem.clear()
" >> "$DATA_DIR/pubmed.ndjson"
done

finalize_ndjson "$DATA_DIR/pubmed.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/pubmed.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/pubmed-baseline.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID  files=${HITORRO_PUBMED_FILES}")

Try:
  SELECT journal, COUNT(*) AS n FROM pubmed GROUP BY journal ORDER BY n DESC LIMIT 20;

Pull the full baseline (~30GB compressed):
  HITORRO_PUBMED_FILES=1200 HITORRO_PUBMED_CONFIRM=1 ./scripts/install-pubmed-baseline.sh

EOF
