#!/usr/bin/env bash
set -euo pipefail
SCM_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCM_JAR="$SCM_ROOT/backend/app/target/app-0.1.0-SNAPSHOT.jar"
[[ -f "$SCM_JAR" ]] || { echo 'Run scripts/build.sh first' >&2; exit 1; }
read -r -a SCM_JAVA_ARGS <<< "${SCM_JAVA_OPTS:--Xms128m -Xmx512m}"
cd -- "$SCM_ROOT"
exec java "${SCM_JAVA_ARGS[@]}" -jar "$SCM_JAR" server "${SCM_CONFIG:-config/development.yaml}"
