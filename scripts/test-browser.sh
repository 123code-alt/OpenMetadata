#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
scm_load_env
export SCM_BASE_URL="${SCM_BASE_URL:-http://127.0.0.1:$SCM_PORT}"
if [[ -z "${SCM_CHROMIUM_PATH:-}" ]] && command -v chromium >/dev/null; then export SCM_CHROMIUM_PATH="$(command -v chromium)"; fi
cd -- "$SCM_ROOT"
exec yarn workspace semantic-context-ui run test:e2e "$@"
