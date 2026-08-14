# Sourced by every install script.
# Defines HITORRO_DATASETS_HOME, download helpers, TSV→NDJSON conversion.
#
# Every dataset lives under $HITORRO_DATASETS_HOME/<dataset-id>/:
#   raw/       — original download, untouched
#   data/      — NDJSON partitions the mesh agent will load
#   types/     — JVS type JSON copied out of the datasets jar
#   manifest.yaml   — the manifest that describes the install

: "${HITORRO_DATASETS_HOME:=$HOME/.hitorro/datasets}"

# BASH_SOURCE resolves to common.sh itself no matter which script sourced us
# or which directory that script has cd'd into. `$0` would be the caller
# script's original path, which is stale after the caller cd's.
MODULE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

info()  { printf "\033[1;34m→\033[0m %s\n" "$*"; }
warn()  { printf "\033[1;33m⚠\033[0m %s\n" "$*" >&2; }
die()   { printf "\033[1;31m✗\033[0m %s\n" "$*" >&2; exit 1; }
ok()    { printf "\033[1;32m✓\033[0m %s\n" "$*"; }

# ---- prerequisites ----
require_cmd() {
    command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

# ---- download ----
# fetch <url> <destfile>
# Uses curl or wget; skips the download if the file already exists AND
# HITORRO_DATASETS_FORCE isn't set.
fetch() {
    local url="$1" dest="$2"
    if [[ -f "$dest" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
        info "cached: $dest — set HITORRO_DATASETS_FORCE=1 to re-download"
        return 0
    fi
    mkdir -p "$(dirname "$dest")"
    if command -v curl >/dev/null 2>&1; then
        info "curl → $url"
        curl -sSL --fail "$url" -o "$dest.part" && mv "$dest.part" "$dest"
    elif command -v wget >/dev/null 2>&1; then
        info "wget → $url"
        wget -q "$url" -O "$dest.part" && mv "$dest.part" "$dest"
    else
        die "need curl or wget on PATH"
    fi
}

# ---- optional bzip compression of NDJSON outputs ----
# By default every install script that writes an NDJSON file also gzip/bzip
# compresses it. Set HITORRO_DATASETS_COMPRESS=0 to opt out (useful for
# quick debugging by hand). Set HITORRO_DATASETS_COMPRESS=gz for gzip
# instead of the default bz2 (gz is faster to decode, bz2 is smaller).
# Mesh agents read through NdjsonLocalTable which transparently decodes
# by extension.
: "${HITORRO_DATASETS_COMPRESS:=bz2}"

# maybe_compress <ndjson_path>
# When compression is on, gzip/bzip the file in-place and echo the new
# path (adds .bz2 / .gz suffix). When off, echo the input path unchanged.
maybe_compress() {
    local path="$1"
    case "$HITORRO_DATASETS_COMPRESS" in
        0|no|off|false|"")
            printf "%s" "$path"
            ;;
        gz)
            require_cmd gzip
            gzip -f "$path"
            printf "%s.gz" "$path"
            ;;
        bz2|bzip|bzip2)
            require_cmd bzip2
            bzip2 -f "$path"
            printf "%s.bz2" "$path"
            ;;
        *)
            warn "unknown HITORRO_DATASETS_COMPRESS=$HITORRO_DATASETS_COMPRESS — writing uncompressed"
            printf "%s" "$path"
            ;;
    esac
}

# finalize_ndjson <uncompressed_path>
# One-line replacement for the `ok "wrote N records to X"` pattern.
# Counts rows, compresses (respecting HITORRO_DATASETS_COMPRESS), reports,
# and writes an installed.json stats file at the install dir root so the
# driver can surface actual numbers in the /mesh/datasets/{id} response
# (instead of drifting hand-maintained stats in the manifest).
# Echoes the final path so callers that need it can capture:
#     final=$(finalize_ndjson "$DATA_DIR/x.ndjson")
finalize_ndjson() {
    local path="$1"
    local rows
    rows=$(wc -l < "$path")
    local final
    final=$(maybe_compress "$path")

    # Sync install-time stats — install dir is the grandparent of the
    # ndjson (data/foo.ndjson.bz2 → install dir). Portable byte size
    # via stat with the macOS/BSD (-f%z) flag falling through to GNU (-c%s).
    local bytes now install_dir file_basename
    bytes=$(stat -f%z "$final" 2>/dev/null || stat -c%s "$final" 2>/dev/null || echo 0)
    now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    install_dir=$(dirname "$(dirname "$final")")
    file_basename=$(basename "$final")
    cat > "$install_dir/installed.json" <<JSON
{
  "rowCount": ${rows// /},
  "sizeBytes": ${bytes},
  "installedAt": "${now}",
  "dataFile": "${file_basename}"
}
JSON

    ok "wrote ${rows// /} records → $final" >&2
    printf "%s" "$final"
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo "sha256-unavailable"
    fi
}

# ---- TSV → NDJSON ----
# tsv_to_ndjson <infile> <outfile> "<col1,col2,...>"
# GeoNames TSV: no header, no quoting, single-tab separator, backslashes literal.
# We build one JSON object per line — numeric fields (population/elevation/lat/lon)
# stay as JSON numbers; empty strings become nulls; everything else is a string.
tsv_to_ndjson() {
    local infile="$1" outfile="$2" cols="$3"
    local numeric="$4"   # comma-separated names that should be JSON numbers
    require_cmd awk
    info "tsv → ndjson: $(basename "$infile") ($(wc -l < "$infile") rows)"
    awk -F'\t' -v cols="$cols" -v numeric="$numeric" '
    BEGIN {
        n = split(cols, name, ",")
        split(numeric, numlist, ",")
        for (i in numlist) numset[numlist[i]] = 1
    }
    /^#/ { next }             # countryInfo.txt has leading comment lines
    NF == 0 { next }
    {
        out = "{"
        first = 1
        for (i = 1; i <= n; i++) {
            v = $i
            if (v == "") { val = "null" }
            else if (numset[name[i]]) { val = v }
            else {
                gsub(/\\/, "\\\\", v)
                gsub(/"/,  "\\\"", v)
                gsub(/\r/, "",     v)
                val = "\"" v "\""
            }
            if (!first) out = out ","
            out = out "\"" name[i] "\":" val
            first = 0
        }
        out = out "}"
        print out
    }' "$infile" > "$outfile"
    finalize_ndjson "$outfile" > /dev/null
}
