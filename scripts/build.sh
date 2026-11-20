#!/usr/bin/env bash
set -euo pipefail
SCM_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
for command in java mvn node yarn; do command -v "$command" >/dev/null || { echo "Missing required tool: $command" >&2; exit 1; }; done
[[ "$(java -version 2>&1)" == *'"21.'* ]] || { echo 'Use JDK 21' >&2; exit 1; }
[[ "$(yarn --version)" == 1.22.* ]] || { echo 'Use Yarn 1.22' >&2; exit 1; }
node -e 'const [a,b,c]=process.versions.node.split(".").map(Number);if(a<22||(a===22&&(b<23||(b===23&&c<4))))process.exit(1)' || { echo 'Use Node 22.23.4 or newer' >&2; exit 1; }
cd -- "$SCM_ROOT"
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 yarn install --frozen-lockfile --non-interactive
yarn run build:frontend
rm -rf -- "$SCM_ROOT/backend/app/target/classes/assets"
mvn -B -ntp -f "$SCM_ROOT/pom.xml" spotless:check package
