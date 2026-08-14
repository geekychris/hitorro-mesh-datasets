#!/usr/bin/env bash
# Reads SQL from stdin, rewrites USING PLACE clauses using the datasets
# registry, prints the result. Reads bundled manifests + anything under
# $HITORRO_DATASETS_HOME.
#
# Usage:
#   echo "SELECT * FROM cities c JOIN countries USING PLACE" | ./scripts/rewrite-sql.sh
#   ./scripts/rewrite-sql.sh < query.sql
set -euo pipefail
cd "$(dirname "$0")/.."

# Chained compile + exec so the classpath always reflects current source
# (exec:java doesn't trigger compile itself; a stale target/classes causes
# ClassNotFoundException on a new CLI). -q suppresses Maven's own noise;
# the CLI writes to stdout / stderr.
exec mvn -q compile exec:java \
    -Dexec.mainClass=com.hitorro.mesh.datasets.cli.RewriteSqlCli
