#!/usr/bin/env bash
set -euo pipefail

NEO4J_AUTH_VALUE="${NEO4J_AUTH:-neo4j/expand123456}"

printf "Running mvn test with NEO4J_AUTH=%s\n" "${NEO4J_AUTH_VALUE}"
NEO4J_AUTH="${NEO4J_AUTH_VALUE}" mvn test
