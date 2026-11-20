#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
scm_load_env
mkdir -p "$SCM_ROOT/.runtime"
SCM_PID_FILE="$SCM_ROOT/.runtime/app.pid"
if [[ -f "$SCM_PID_FILE" ]]; then
  SCM_PID="$(cat "$SCM_PID_FILE")"
  if [[ "$SCM_PID" =~ ^[0-9]+$ ]] && kill -0 "$SCM_PID" 2>/dev/null; then
    SCM_COMMAND="$(ps -p "$SCM_PID" -o args=)"
    [[ "$SCM_COMMAND" != *"-jar $SCM_ROOT/"* ]] || { echo "Application already running (PID $SCM_PID)."; exit 0; }
  fi
fi
for SCM_CHECK_PORT in "$SCM_PORT" "$SCM_ADMIN_PORT"; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$SCM_CHECK_PORT") 2>/dev/null; then
    echo "Port $SCM_CHECK_PORT is already in use; choose different ports in .env." >&2
    exit 1
  fi
done
nohup bash "$SCM_ROOT/scripts/run.sh" </dev/null >>"$SCM_ROOT/.runtime/server.log" 2>&1 &
SCM_PID=$!
echo "$SCM_PID" > "$SCM_PID_FILE"
for (( SCM_ATTEMPT=0; SCM_ATTEMPT<90; SCM_ATTEMPT++ )); do
  kill -0 "$SCM_PID" 2>/dev/null || { echo 'Application exited. Inspect .runtime/server.log.' >&2; exit 1; }
  if curl --noproxy '*' -fsS --max-time 1 "http://127.0.0.1:$SCM_ADMIN_PORT/healthcheck" >/dev/null 2>&1; then
    echo "Application ready on port $SCM_PORT (PID $SCM_PID)."
    exit 0
  fi
  sleep 1
done
echo 'Startup timed out. Inspect .runtime/server.log; stop with scripts/stop.sh.' >&2
exit 1
