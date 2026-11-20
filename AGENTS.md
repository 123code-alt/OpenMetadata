# Semantic Context minimal project

- Backend: JDK 21, Dropwizard/JDBI. Maven root contains only the five backend modules.
- Frontend: Yarn 1 workspaces (`frontend`, `ui-core`), React and static Vite assets. Use Yarn, not npm.
- `ui-core` is the dependency closure of the 18 components used by this UI. Preserve upstream license headers; record copied-file changes in `SOURCE_MANIFEST.json`.
- JSON Schemas and generated TypeScript models are upstream contracts. Do not hand-edit generated models.
- Build and validate with `bash scripts/build.sh`; run browser flows with `yarn run test:e2e` against a running application.
- Run `mvn spotless:apply` after Java changes, then verify with `mvn spotless:check test`.
- Keep `.env`, runtime data, dependencies and build output out of Git.
- Preserve the current feature subset. Full workflow, semantic search, original full UI pages and MCP are outside this prototype.
