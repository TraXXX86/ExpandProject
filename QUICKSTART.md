# Quick start

ExpandProject runs with Java 17, Node.js 24, Neo4j 5.26.31, and Maven 3.9.9. `./mvnw` downloads Maven from the Apache archive and verifies its SHA-512 checksum.

## Start all services with Docker Compose

```bash
cp .env.example .env
# Set unique NEO4J_AUTH=neo4j/<password> and EXPAND_ADMIN_PASSWORD values in .env.
docker compose up --build
```

Compose waits for Neo4j and the API to become healthy. Open:

- UI: http://localhost:5173
- API: http://localhost:8080
- Neo4j Browser: http://localhost:7474

The API creates the `admin` account from `EXPAND_ADMIN_PASSWORD` only when its SQLite access database is empty. There are no default passwords. Neo4j and SQLite data persist in named volumes; service ports bind to localhost.

## Run the CLI example

```bash
./mvnw -pl importdata -am package
cd importdata
../mvnw exec:java \
  -Dexec.mainClass="fr.expand.project.importdata.Launcher" \
  -Dexec.args="--example"
```

For a Neo4j import, export `NEO4J_BOLT_URI`, `NEO4J_AUTH`, and `EXPAND_ADMIN_PASSWORD` before launching the CLI with your model and data files.

## Run services locally

Start Neo4j using the credentials supplied through `NEO4J_AUTH`, then build and launch the API:

```bash
export NEO4J_BOLT_URI=bolt://localhost:7687
export NEO4J_AUTH='neo4j/<your-password>'
export EXPAND_ADMIN_PASSWORD='<your-admin-password>'
export ACCESS_DB_PATH="$PWD/access.sqlite"
./mvnw -pl importdata -am package
java -jar importdata/target/expandproject-importdata.jar --api 8080
```

In another terminal:

```bash
cd ui
npm ci
npm run dev
```

For test and API migration details, see [README.md](README.md).
