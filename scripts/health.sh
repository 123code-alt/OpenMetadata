#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/common.sh"
scm_load_env
curl --noproxy '*' -fsS --max-time 5 "http://127.0.0.1:$SCM_ADMIN_PORT/healthcheck"
echo
