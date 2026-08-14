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

# Chained compile + exec so the classpath always reflects current source.
# Pass all flags through as one exec.args string so the CLI's arg parser
# (not shell) handles them.
ARGS="$*"
exec mvn -q compile exec:java \
    -Dexec.mainClass=com.hitorro.mesh.datasets.cli.RegisterInstalledCli \
    -Dexec.args="$ARGS"
