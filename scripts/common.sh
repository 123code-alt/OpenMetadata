#!/usr/bin/env bash
SCM_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

scm_load_env() {
  local file="${SCM_ENV_FILE:-$SCM_ROOT/.env}"
  [[ -f "$file" ]] || { echo 'Run bash scripts/init-env.sh first, or set SCM_ENV_FILE.' >&2; return 1; }
  set -a
  source "$file"
  set +a
  : "${SCM_PORT:=8585}" "${SCM_ADMIN_PORT:=8586}"
  [[ -n "${SCM_DB_PASSWORD:-}" && -n "${SCM_API_TOKEN:-}" ]] || { echo 'SCM_DB_PASSWORD and SCM_API_TOKEN must be set in the environment file.' >&2; return 1; }
  for value in "$SCM_PORT" "$SCM_ADMIN_PORT"; do
    [[ "$value" =~ ^[0-9]+$ ]] && (( value > 0 && value < 65536 )) || { echo 'Invalid application/admin port.' >&2; return 1; }
  done
  export SCM_PORT SCM_ADMIN_PORT
}
