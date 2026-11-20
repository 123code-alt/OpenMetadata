#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
scm_load_env
SCM_JAR="$SCM_ROOT/semantic-context.jar"
[[ -f "$SCM_JAR" ]] || SCM_JAR="$SCM_ROOT/backend/app/target/app-0.1.0-SNAPSHOT.jar"
[[ -f "$SCM_JAR" ]] || { echo 'Run scripts/build.sh first, or unpack the runtime archive.' >&2; exit 1; }
[[ "$(java -version 2>&1)" == *'"21.'* ]] || { echo 'Use JDK 21' >&2; exit 1; }
read -r -a SCM_JAVA_ARGS <<< "${SCM_JAVA_OPTS:--Xms128m -Xmx512m}"
cd -- "$SCM_ROOT"
exec java "${SCM_JAVA_ARGS[@]}" -jar "$SCM_JAR" server "$SCM_ROOT/config/server.yaml"
