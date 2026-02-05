#!/usr/bin/env bash
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080}"
MODEL_FILE="${MODEL_FILE:-importdata/src/main/resources/model/example_social_network_model.xml}"

if [ ! -f "${MODEL_FILE}" ]; then
  echo "Model file not found: ${MODEL_FILE}" >&2
  exit 1
fi

echo "==> Uploading model to ${API_BASE}"
curl -sS -f -F "modelFile=@${MODEL_FILE}" "${API_BASE}/api/models" | tee /tmp/expandproject-model-upload.json

echo ""
echo "==> Listing models"
curl -sS -f "${API_BASE}/api/models" | tee /tmp/expandproject-model-list.json

echo ""
echo "OK: results saved to /tmp/expandproject-model-*.json"
