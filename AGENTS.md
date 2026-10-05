# ExpandProject contributor notes

## Architecture

| Service | Technology | Port | Start command |
|---------|-----------|------|---------------|
| Neo4j | Neo4j 5.26.31 | 7474 / 7687 | `docker compose up neo4j` |
| API | Java 17, Javalin 7, Maven 3.9.9 | 8080 | `java -jar importdata/target/expandproject-importdata.jar --api 8080` |
| UI | Vue 3, Vuetify, Vite, Node 24 | 5173 (dev) / 80 (Compose) | `cd ui && npm ci && npm run dev` |

The API uses Neo4j over Bolt and SQLite for user, role, and session data. JAXB classes are generated under `importdata/target/generated-sources/jaxb`; generated files do not belong in `src/main/java`.

## Local setup

1. Install Java 17 and Docker Compose. `./mvnw` downloads and verifies Apache Maven 3.9.9 when it is not cached.
2. Copy `.env.example` to `.env` and set distinct values for `NEO4J_AUTH` (`neo4j/<password>`) and `EXPAND_ADMIN_PASSWORD`. There are no default service credentials.
3. Start the services with `docker compose up --build`. Compose waits for Neo4j and the API health checks; the `neo4j_data` and `access_data` volumes persist state.
4. For a local API run, export `NEO4J_BOLT_URI`, `NEO4J_AUTH`, and `EXPAND_ADMIN_PASSWORD`. Set `ACCESS_DB_PATH` to choose the SQLite file location.
5. For deployments, set `EXPAND_ALLOWED_ORIGINS` to the explicit comma-separated UI origin allowlist and enable `EXPAND_COOKIE_SECURE=true` when the API is served over HTTPS.

The API creates the `admin` account with `EXPAND_ADMIN_PASSWORD` only when the access database is empty. Existing accounts and roles stay in SQLite. Successful logins progressively upgrade older password hashes to the current PBKDF2 format. Session tokens expire after 12 hours; reset sessions by clearing the SQLite `sessions` table when required.

## Build and checks

- Backend build: `./mvnw -pl importdata -am package`
- Backend unit tests: `./mvnw -pl importdata -am test`
- Backend integration tests: `./mvnw -pl importdata -am verify` with Neo4j available and disposable `NEO4J_AUTH`, `NEO4J_BOLT_URI`, `EXPAND_ADMIN_PASSWORD`, and `ACCESS_DB_PATH` values.
- Frontend checks: `cd ui && npm ci && npm test && npm audit --audit-level=high && npm run build`

JUnit 4 remains the test framework. Failsafe integration-test classes use the `*IT` suffix and run during `verify`. Do not print credentials or session tokens in scripts or CI logs.
