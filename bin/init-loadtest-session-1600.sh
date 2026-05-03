#!/usr/bin/env bash

set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${BIN_DIR}/.." && pwd)"
BASE_URL="${BASE_URL:-http://127.0.0.1}"
PAYLOAD="${ROOT_DIR}/data/loadtest-1600-session-init.json"

echo "init session payload=${PAYLOAD}"
curl -X POST "${BASE_URL}/api/seckill/init" \
  -H 'Content-Type: application/json' \
  --data @"${PAYLOAD}"
echo
