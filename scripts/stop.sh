#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
SCM_PID_FILE="$SCM_ROOT/.runtime/app.pid"
[[ -f "$SCM_PID_FILE" ]] || { echo 'Application is stopped.'; exit 0; }
SCM_PID="$(cat "$SCM_PID_FILE")"
[[ "$SCM_PID" =~ ^[0-9]+$ ]] || { echo 'Invalid PID file.' >&2; exit 1; }
if ! kill -0 "$SCM_PID" 2>/dev/null; then rm -f "$SCM_PID_FILE"; echo 'Application is stopped.'; exit 0; fi
SCM_COMMAND="$(ps -p "$SCM_PID" -o args=)"
[[ "$SCM_COMMAND" == *"-jar $SCM_ROOT/"* ]] || { echo 'PID does not belong to this application; refusing to stop it.' >&2; exit 1; }
kill "$SCM_PID"
for (( SCM_ATTEMPT=0; SCM_ATTEMPT<30; SCM_ATTEMPT++ )); do
  SCM_STATE="$(ps -p "$SCM_PID" -o stat= || true)"
  if [[ -z "$SCM_STATE" || "$SCM_STATE" == Z* ]]; then rm -f "$SCM_PID_FILE"; echo 'Application stopped.'; exit 0; fi
  sleep 1
done
echo 'Shutdown still in progress; PID file retained.' >&2
exit 1
