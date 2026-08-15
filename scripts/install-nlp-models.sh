#!/usr/bin/env bash
# OpenNLP MaxEnt models — sentence detector, tokenizer, POS tagger, NER —
# for the JVS enrichment step (jvs-enrich). Downloads .bin files into
# ${HT_BIN}/data/opennlpmodels1.5/ where the type system's dynamic
# mappers look for them.
#
# Default installs English models only (~50MB). Set HITORRO_NLP_LANGS to
# a comma-separated list of ISO 639-1 codes to install more languages.
#
# Tunables:
#   HT_BIN=/path/to/hitorro           # target root (defaults to $HOME/hitorro)
#   HITORRO_NLP_LANGS=en,es,fr,de     # comma-separated language codes
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

: "${HT_BIN:=$HOME/hitorro}"
: "${HITORRO_NLP_LANGS:=en}"

MODEL_DIR="$HT_BIN/data/opennlpmodels1.5"
mkdir -p "$MODEL_DIR"

# Apache OpenNLP model repository. The 1.5 series is smaller / older
# than the current 2.x line but is what the shipped hitorro dynamic
# mappers were trained against — mixing model versions crashes at load.
BASE="https://opennlp.sourceforge.net/models-1.5"

# Per-language model catalog. Each entry: "<local-name>=<remote-name>".
# Not every language has every model — the loop skips 404s.
_models_for() {
    local lang=$1
    case "$lang" in
        en)
            printf "%s\n" \
                "en-sent.bin=en-sent.bin" \
                "en-token.bin=en-token.bin" \
                "en-pos-maxent.bin=en-pos-maxent.bin" \
                "en-pos-perceptron.bin=en-pos-perceptron.bin" \
                "en-ner-person.bin=en-ner-person.bin" \
                "en-ner-location.bin=en-ner-location.bin" \
                "en-ner-organization.bin=en-ner-organization.bin" \
                "en-ner-date.bin=en-ner-date.bin" \
                "en-ner-money.bin=en-ner-money.bin" \
                "en-ner-percentage.bin=en-ner-percentage.bin" \
                "en-ner-time.bin=en-ner-time.bin" \
                "en-chunker.bin=en-chunker.bin" \
                "en-parser-chunking.bin=en-parser-chunking.bin"
            ;;
        es)
            printf "%s\n" \
                "es-sent.bin=es-sent.bin" \
                "es-token.bin=es-token.bin" \
                "es-pos-maxent.bin=es-pos-maxent.bin" \
                "es-pos-perceptron.bin=es-pos-perceptron.bin" \
                "es-ner-person.bin=es-ner-person.bin" \
                "es-ner-location.bin=es-ner-location.bin" \
                "es-ner-organization.bin=es-ner-organization.bin" \
                "es-ner-misc.bin=es-ner-misc.bin"
            ;;
        fr|de|it|nl|pt|da|pl|se)
            printf "%s\n" \
                "${lang}-sent.bin=${lang}-sent.bin" \
                "${lang}-token.bin=${lang}-token.bin"
            ;;
        *)
            warn "no known OpenNLP models for '$lang' — skipping"
            ;;
    esac
}

for lang in $(echo "$HITORRO_NLP_LANGS" | tr ',' ' '); do
    info "installing models for '$lang' → $MODEL_DIR"
    while IFS='=' read -r local remote; do
        [[ -z "$local" ]] && continue
        dest="$MODEL_DIR/$local"
        if [[ -f "$dest" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
            info "  cached: $local"
            continue
        fi
        url="$BASE/$remote"
        info "  fetch $remote"
        if curl -sSfL "$url" -o "$dest.part"; then
            mv "$dest.part" "$dest"
        else
            rm -f "$dest.part"
            warn "  $remote not available (skipping)"
        fi
    done < <(_models_for "$lang")
done

echo
ok "installed to $MODEL_DIR"
ls -lh "$MODEL_DIR" | tail -20
cat <<EOF

Available languages configured via ISO 639-1 codes:
  full:  en (sentence + tokenizer + POS + full NER + chunker + parser)
  full:  es (sentence + tokenizer + POS + NER)
  base:  fr, de, it, nl, pt, da, pl, se (sentence + tokenizer only)

Enable in the pipeline:
  # enrich-articles.yaml uses these mappers automatically via jvs-enrich

Add more languages:
  HITORRO_NLP_LANGS=en,es,fr,de,it ./scripts/install-nlp-models.sh
EOF
