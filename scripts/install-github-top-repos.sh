#!/usr/bin/env bash
# GitHub top-100 repos by star count. One search-API call, no auth.
# Refresh via HITORRO_DATASETS_FORCE=1.
set -euo pipefail
cd "$(dirname "$0")"
source ./common.sh

DATASET_ID="github-top-repos"
URL="https://api.github.com/search/repositories?q=stars:%3E100000&sort=stars&order=desc&per_page=100"

INSTALL_DIR="$HITORRO_DATASETS_HOME/$DATASET_ID"
RAW_DIR="$INSTALL_DIR/raw"
DATA_DIR="$INSTALL_DIR/data"
TYPES_DIR="$INSTALL_DIR/types"

mkdir -p "$RAW_DIR" "$DATA_DIR" "$TYPES_DIR"

require_cmd jq
require_cmd curl

# GitHub asks for a User-Agent; unauthenticated is fine (60 req/hr).
# Set GITHUB_TOKEN in the env to lift to 5000/hr — not required here
# since we only make one call.
if [[ -f "$RAW_DIR/repos.json" && -z "${HITORRO_DATASETS_FORCE:-}" ]]; then
    info "cached: $RAW_DIR/repos.json — set HITORRO_DATASETS_FORCE=1 to refresh"
else
    info "GitHub search API →"
    auth_header=()
    if [[ -n "${GITHUB_TOKEN:-}" ]]; then
        auth_header=(-H "Authorization: Bearer $GITHUB_TOKEN")
        info "  using GITHUB_TOKEN (higher rate limit)"
    fi
    curl -sSfL \
        -H "User-Agent: hitorro-mesh-datasets/3.0.1 (https://github.com/geekychris/hitorro-mesh-datasets)" \
        -H "Accept: application/vnd.github+json" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "${auth_header[@]}" \
        "$URL" -o "$RAW_DIR/repos.json.part"
    mv "$RAW_DIR/repos.json.part" "$RAW_DIR/repos.json"
fi
info "sha256: $(sha256_of "$RAW_DIR/repos.json")"

info "json → ndjson (jq)"
# Response has .items[]. License is nested at .license.spdx_id. Topics
# come back as an array — flatten to a comma-separated string so downstream
# queries can do LIKE '%awesome%' without exploding JSON.
jq -c '.items[] | {
    github_id:         .id,
    full_name:         .full_name,
    name:              .name,
    owner_login:       .owner.login,
    owner_type:        .owner.type,
    description:       .description,
    stargazers_count:  .stargazers_count,
    forks_count:       .forks_count,
    watchers_count:    .watchers_count,
    open_issues_count: .open_issues_count,
    primary_language:  .language,
    license_spdx:      ((.license // {}).spdx_id // null),
    homepage:          .homepage,
    topics:            ((.topics // []) | join(",")),
    created_at:        .created_at,
    updated_at:        .updated_at
}' "$RAW_DIR/repos.json" > "$DATA_DIR/repos.ndjson"

finalize_ndjson "$DATA_DIR/repos.ndjson" > /dev/null

cp "$MODULE_ROOT/src/main/resources/types/github_top_repos.json" "$TYPES_DIR/"
cp "$MODULE_ROOT/src/main/resources/manifests/github-top-repos.yaml" "$INSTALL_DIR/manifest.yaml"

cat <<EOF

$(ok "installed $DATASET_ID")

Try in the Datasets tab:

  # Top 10 by stars — the classic
  SELECT full_name, stargazers_count, primary_language, license_spdx
  FROM github_top_repos
  ORDER BY stargazers_count DESC LIMIT 10;

  # Language distribution
  SELECT primary_language, COUNT(*) AS n, SUM(stargazers_count) AS total_stars
  FROM github_top_repos
  WHERE primary_language IS NOT NULL
  GROUP BY primary_language ORDER BY n DESC;

  # Most engaged (highest star-to-fork ratio, min 10 forks)
  SELECT full_name, stargazers_count, forks_count,
         (stargazers_count / forks_count) AS stars_per_fork
  FROM github_top_repos
  WHERE forks_count >= 10
  ORDER BY stars_per_fork DESC LIMIT 15;

  # 'Awesome list' repos
  SELECT full_name, stargazers_count, description
  FROM github_top_repos
  WHERE topics LIKE '%awesome%'
  ORDER BY stargazers_count DESC LIMIT 15;

Refresh with:  HITORRO_DATASETS_FORCE=1 ./scripts/install-github-top-repos.sh
Rate-limit workaround: set GITHUB_TOKEN in the env for 5000/hr instead of 60/hr.

EOF
