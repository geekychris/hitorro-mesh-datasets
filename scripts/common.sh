# Sourced by every install script.
# Defines HITORRO_DATASETS_HOME, download helpers, TSV→NDJSON conversion.
#
# Every dataset lives under $HITORRO_DATASETS_HOME/<dataset-id>/:
#   raw/       — original download, untouched
#   data/      — NDJSON partitions the mesh agent will load
#   types/     — JVS type JSON copied out of the datasets jar
#   manifest.yaml   — the manifest that describes the install

: "${HITORRO_DATASETS_HOME:=$HOME/.hitorro/datasets}"

MODULE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

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
    ok "wrote $(wc -l < "$outfile") records to $outfile"
}
