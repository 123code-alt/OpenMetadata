#!/usr/bin/env bash
set -euo pipefail
SCM_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCM_ENV_PATH="${SCM_ENV_FILE:-$SCM_ROOT/.env}"
[[ ! -e "$SCM_ENV_PATH" ]] || { echo "Existing configuration preserved: $SCM_ENV_PATH"; exit 0; }
command -v openssl >/dev/null || { echo 'Install openssl or fill .env.example manually.' >&2; exit 1; }
SCM_DB_PORT="${SCM_DB_PORT:-5432}"
SCM_PORT="${SCM_PORT:-8585}"
SCM_ADMIN_PORT="${SCM_ADMIN_PORT:-8586}"
for SCM_VALUE in "$SCM_DB_PORT" "$SCM_PORT" "$SCM_ADMIN_PORT"; do
  [[ "$SCM_VALUE" =~ ^[0-9]+$ ]] && (( SCM_VALUE > 0 && SCM_VALUE < 65536 )) || { echo 'Invalid port.' >&2; exit 1; }
done
umask 077
set -o noclobber
cat > "$SCM_ENV_PATH" <<EOF
SCM_DB_PASSWORD=$(openssl rand -hex 24)
SCM_API_TOKEN=$(openssl rand -hex 32)
SCM_DB_PORT=$SCM_DB_PORT
SCM_PORT=$SCM_PORT
SCM_ADMIN_PORT=$SCM_ADMIN_PORT
SCM_BIND_HOST=0.0.0.0
SCM_DB_USER=semantic_context
SCM_JDBC_URL=jdbc:postgresql://127.0.0.1:$SCM_DB_PORT/semantic_context
SCM_BLOB_DIRECTORY=.runtime/blobs
SCM_JAVA_OPTS="-Xms128m -Xmx512m"
EOF
echo "Configuration generated: $SCM_ENV_PATH"
echo 'Copy SCM_API_TOKEN from this file into the web sign-in form. Keep this file for future restarts.'
