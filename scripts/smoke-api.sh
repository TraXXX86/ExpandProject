#!/usr/bin/env bash
set -euo pipefail
set +x

API_BASE="${API_BASE:-http://localhost:8080}"
API_USERNAME="${API_USERNAME:-admin}"
MODEL_FILE="${MODEL_FILE:-importdata/src/main/resources/model/example_social_network_model.xml}"

if [ -z "${EXPAND_ADMIN_PASSWORD:-}" ]; then
  echo "Set EXPAND_ADMIN_PASSWORD to the password configured for the API admin account." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to parse the API login response." >&2
  exit 1
fi
if [ ! -f "${MODEL_FILE}" ]; then
  echo "Model file not found: ${MODEL_FILE}" >&2
  exit 1
fi

tmp_dir=$(mktemp -d)
chmod 700 "${tmp_dir}"
trap 'rm -rf "${tmp_dir}"' EXIT
API_USERNAME="${API_USERNAME}" EXPAND_ADMIN_PASSWORD="${EXPAND_ADMIN_PASSWORD}" \
  jq -n '{username: env.API_USERNAME, password: env.EXPAND_ADMIN_PASSWORD}' > "${tmp_dir}/login.json"
chmod 600 "${tmp_dir}/login.json"
curl --silent --show-error --fail \
  --header 'Content-Type: application/json' \
  --data-binary "@${tmp_dir}/login.json" \
  "${API_BASE}/api/auth/login" > "${tmp_dir}/login-response.json"
token=$(jq -er '.token' "${tmp_dir}/login-response.json")

printf 'Uploading model to %s\n' "${API_BASE}"
curl --silent --show-error --fail --output "${tmp_dir}/model-upload.json" \
  --header "Authorization: Bearer ${token}" \
  --form "modelFile=@${MODEL_FILE}" \
  "${API_BASE}/api/models"
printf 'Listing models\n'
curl --silent --show-error --fail --output "${tmp_dir}/model-list.json" \
  --header "Authorization: Bearer ${token}" \
  "${API_BASE}/api/models"
printf 'OK: authenticated model upload and list completed.\n'
