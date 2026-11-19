#!/usr/bin/env bash
set -euo pipefail
SCM_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCM_REPO="$(cd -- "$SCM_ROOT/../.." && pwd)"
SCM_CORE="$SCM_REPO/openmetadata-ui-core-components/src/main/resources/ui"

for command in java mvn node yarn; do command -v "$command" >/dev/null || { echo "Missing required tool: $command" >&2; exit 1; }; done
[[ "$(java -version 2>&1)" == *'"21.'* ]] || { echo 'Use JDK 21' >&2; exit 1; }
[[ "$(yarn --version)" == 1.22.* ]] || { echo 'Use Yarn 1.22' >&2; exit 1; }
node -e 'const [major,minor,patch]=process.versions.node.split(".").map(Number);if(major<22||(major===22&&(minor<23||(minor===23&&patch<4))))process.exit(1)' || { echo 'Use Node 22.23.4 or newer' >&2; exit 1; }

PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 yarn --cwd "$SCM_ROOT/frontend" install --frozen-lockfile --non-interactive
if [[ ! -e "$SCM_CORE/node_modules" && ! -L "$SCM_CORE/node_modules" ]]; then
  ln -s "$SCM_ROOT/frontend/node_modules" "$SCM_CORE/node_modules"
fi
yarn --cwd "$SCM_CORE" run build
yarn --cwd "$SCM_ROOT/frontend" run lint
yarn --cwd "$SCM_ROOT/frontend" run build
rm -rf -- "$SCM_ROOT/backend/app/target/classes/assets"
mvn -f "$SCM_ROOT/pom.xml" spotless:check package
