# Start here

## Docker Compose (recommended)

1. Install Docker Compose.
2. Copy `.env.example` to `.env`, then set unique `NEO4J_AUTH` and `EXPAND_ADMIN_PASSWORD` values. Compose will not start with unset values.
3. Run `docker compose up --build`.
4. Open the UI at http://localhost:5173, the API at http://localhost:8080, and Neo4j Browser at http://localhost:7474.

The API admin account is created with `EXPAND_ADMIN_PASSWORD` when the SQLite access database is first initialized. Neo4j and SQLite data persist in Docker volumes.

## Local development

Install Java 17, Node.js 24, and Docker. Start Neo4j with your chosen `NEO4J_AUTH`, then export `NEO4J_BOLT_URI`, `NEO4J_AUTH`, and `EXPAND_ADMIN_PASSWORD`. Build and launch the API with:

```bash
./mvnw -pl importdata -am package
java -jar importdata/target/expandproject-importdata.jar --api 8080
```

Then run the UI:

```bash
cd ui
npm ci
npm run dev
```

See [README.md](README.md) for CLI import, API configuration, testing, and the migration guide.
