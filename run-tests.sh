#!/usr/bin/env bash
set -euo pipefail

: "${NEO4J_AUTH:?Set NEO4J_AUTH to neo4j/<disposable-or-local-password>}"
: "${EXPAND_ADMIN_PASSWORD:?Set EXPAND_ADMIN_PASSWORD for the local admin account}"
NEO4J_BOLT_URI="${NEO4J_BOLT_URI:-bolt://localhost:7687}"
ACCESS_DB_PATH="${ACCESS_DB_PATH:-${TMPDIR:-/tmp}/expandproject-access.sqlite}"
export NEO4J_BOLT_URI ACCESS_DB_PATH

./mvnw verify
