#!/usr/bin/env bash
set -euo pipefail

UI_DIR="$(cd "$(dirname "$0")" && pwd)/ui"

if [ ! -d "${UI_DIR}" ]; then
  echo "UI directory not found: ${UI_DIR}"
  exit 1
fi

cd "${UI_DIR}"

if [ ! -d node_modules ]; then
  echo "Installing UI dependencies..."
  npm install
fi

echo "Starting UI dev server..."
npm run dev
