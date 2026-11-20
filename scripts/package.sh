#!/usr/bin/env bash
set -euo pipefail
SCM_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCM_JAR="$SCM_ROOT/backend/app/target/app-0.1.0-SNAPSHOT.jar"
[[ -f "$SCM_JAR" ]] || { echo 'Run scripts/build.sh first.' >&2; exit 1; }
SCM_STAGE="$(mktemp -d "${TMPDIR:-/tmp}/semantic-context-package.XXXXXX")"
trap 'rm -rf -- "$SCM_STAGE"' EXIT
SCM_DEST="$SCM_STAGE/semantic-context"
mkdir -p "$SCM_DEST/scripts" "$SCM_DEST/config" "$SCM_ROOT/dist"
cp "$SCM_JAR" "$SCM_DEST/semantic-context.jar"
cp "$SCM_ROOT/config/server.yaml" "$SCM_DEST/config/"
for SCM_SCRIPT in common init-env run start stop health; do cp "$SCM_ROOT/scripts/$SCM_SCRIPT.sh" "$SCM_DEST/scripts/"; done
cp "$SCM_ROOT/LICENSE" "$SCM_ROOT/NOTICE" "$SCM_ROOT/compose.dependencies.yml" "$SCM_ROOT/.env.example" "$SCM_DEST/"
cp "$SCM_ROOT/docs/RUNTIME.md" "$SCM_DEST/README.md"
printf 'upstream=OpenMetadata 2.0.8-release\nprototype=3c17586028ec914a40eb8c56bb5997fb6613dc3e\nsource_commit=%s\n' "$(git -C "$SCM_ROOT" rev-parse --verify HEAD 2>/dev/null || printf working-tree)" > "$SCM_DEST/BUILD_INFO"
tar -C "$SCM_STAGE" -czf "$SCM_ROOT/dist/semantic-context.tar.gz" semantic-context
echo "Runtime archive created: $SCM_ROOT/dist/semantic-context.tar.gz"
