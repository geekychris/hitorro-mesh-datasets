#!/usr/bin/env bash
# Register every dataset installed under $HITORRO_DATASETS_HOME with a
# running driver. Invokes the CLI via mvn exec:java — no shaded jar
# needed, and picks up the current module state.
#
# Usage:
#   ./scripts/register-installed.sh                    # driver http://localhost:8085
#   ./scripts/register-installed.sh --driver URL       # custom driver
#   ./scripts/register-installed.sh --skip docs,demo   # leave these alone
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ ! -d target/classes ]]; then
    ./mvnw -q compile 2>/dev/null || mvn -q compile
fi

# Pass all flags through as one exec.args string so the CLI's arg parser
# (not shell) handles them.
ARGS="$*"
exec mvn -q exec:java \
    -Dexec.mainClass=com.hitorro.mesh.datasets.cli.RegisterInstalledCli \
    -Dexec.args="$ARGS"
