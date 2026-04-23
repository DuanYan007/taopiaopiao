#!/usr/bin/env bash

set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${BIN_DIR}/.." && pwd)"
LOADTEST_DIR="${ROOT_DIR}/scripts/loadtest"
LOG_DIR="${ROOT_DIR}/logs/loadtest"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="${LOG_DIR}/lock-only-burst-${TIMESTAMP}.log"

mkdir -p "${LOG_DIR}"

echo "lock_only_burst log=${LOG_FILE}"

set +e
docker run --rm --network host \
    -e SESSION_ID="${SESSION_ID:-2}" \
    -e USERS="${USERS:-1600}" \
    -e USER_BASE="${USER_BASE:-100000}" \
    -e SEAT_START_ID="${SEAT_START_ID:-161}" \
    -e SEAT_END_ID="${SEAT_END_ID:-1760}" \
    -e SEATS_PER_REQUEST="${SEATS_PER_REQUEST:-1}" \
    -e MAX_ATTEMPTS_PER_USER="${MAX_ATTEMPTS_PER_USER:-20}" \
    -e REQUEST_INTERVAL_SECONDS="${REQUEST_INTERVAL_SECONDS:-1}" \
    -e MAX_DURATION="${MAX_DURATION:-30s}" \
    -e STATUS0_LOG_LIMIT_PER_VU="${STATUS0_LOG_LIMIT_PER_VU:-1}" \
    -v "${LOADTEST_DIR}:/scripts" \
    grafana/k6 run /scripts/lock_only_burst.js 2>&1 | tee "${LOG_FILE}"
status=${PIPESTATUS[0]}
set -e

echo "lock_only_burst exit_code=${status} log=${LOG_FILE}"
exit "${status}"
