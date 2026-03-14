#!/usr/bin/env bash
set -euo pipefail

if [ -f ".env" ]; then
  set -a
  . ".env"
  set +a
fi

: "${NEO4J_AUTH:?Set NEO4J_AUTH in the environment or in .env before running tests}"

printf "Running mvn test with configured Neo4j credentials\n"
NEO4J_AUTH="${NEO4J_AUTH}" mvn test
