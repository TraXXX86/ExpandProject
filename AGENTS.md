# AGENTS.md

## Cursor Cloud specific instructions

### Architecture overview

ExpandProject is a data modeling and import platform for Neo4j graph databases with three components:

| Service | Technology | Port | Start command |
|---------|-----------|------|---------------|
| **Neo4j** | Neo4j 5.17.0 (Docker) | 7474/7687 | `docker run -d --name neo4j-expand -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/expand123456 neo4j:5.17.0` |
| **API** | Java 17+ / SparkJava / Maven | 8080 | `NEO4J_AUTH=neo4j/expand123456 NEO4J_BOLT_URI=bolt://localhost:7687 java -jar importdata/target/expandproject-importdata.jar --api 8080` |
| **UI** | Vue 3 / Vuetify / Vite | 5173 | `cd ui && VITE_API_BASE=http://localhost:8080 npx vite --host 0.0.0.0 --port 5173` |

### Running services

1. **Neo4j must start first** — the API connects to it on startup. Wait ~10s for Neo4j to accept connections before starting the API.
2. **Build the backend JAR** before starting the API: `mvn clean install -DskipTests` (from repo root). The fat JAR is at `importdata/target/expandproject-importdata.jar`.
3. **UI dependencies**: `cd ui && npm ci` before starting the dev server.
4. The API uses **embedded SQLite** for auth (auto-created at `~/.expandproject/access.sqlite`). No extra DB setup needed.

### Default credentials

- **Neo4j**: `neo4j` / `expand123456` (Docker) or `neo4j` / `expand` (local install)
- **API admin**: `admin` / `admin` (auto-bootstrapped on first run)

### Testing

- Backend tests: `NEO4J_AUTH=neo4j/expand123456 NEO4J_BOLT_URI=bolt://localhost:7687 mvn test -pl importdata -DfailIfNoTests=false` (requires running Neo4j).
- Frontend build check: `cd ui && npm run build`
- The CI workflow (`.github/workflows/ci.yml`) runs backend tests with a Neo4j service container and a frontend build check.

### Gotchas

- Java 21 is installed on the VM but the project targets Java 17 (`maven.compiler.source/target=17`). This is compatible; no downgrade needed.
- The default Maven surefire version (2.12.4) silently reports "no tests" even when test classes exist — use `-DfailIfNoTests=false` and explicit `-Dtest=...` if needed.
- The API requires auth via `Authorization: Bearer <token>` header. Get a token by POSTing to `/api/auth/login` with `{"username":"admin","password":"admin"}`.
- Docker must use `fuse-overlayfs` storage driver and `iptables-legacy` in the Cloud VM (nested container environment).
