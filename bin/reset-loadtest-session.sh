#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "usage: $0 <session_id> <init_payload_json>"
    exit 1
fi

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${BIN_DIR}/.." && pwd)"

SESSION_ID="$1"
PAYLOAD_PATH="$2"

BASE_URL="${BASE_URL:-http://127.0.0.1}"
MYSQL_HOST="${MYSQL_HOST:-192.168.3.36}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-taopiaopiao}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"

if [[ ! -f "${PAYLOAD_PATH}" ]]; then
    echo "payload file not found: ${PAYLOAD_PATH}"
    exit 1
fi

if [[ -z "${MYSQL_PASSWORD}" ]]; then
    echo "MYSQL_PASSWORD is required"
    exit 1
fi

run_mysql() {
    MYSQL_PWD="${MYSQL_PASSWORD}" mysql \
        -h "${MYSQL_HOST}" \
        -P "${MYSQL_PORT}" \
        -u "${MYSQL_USER}" \
        "${MYSQL_DB}" "$@"
}

echo "== RESET LOADTEST SESSION =="
printf 'session_id=%s\n' "${SESSION_ID}"
printf 'payload=%s\n' "${PAYLOAD_PATH}"
printf 'mysql=%s:%s/%s\n' "${MYSQL_HOST}" "${MYSQL_PORT}" "${MYSQL_DB}"

echo "== MYSQL RESET =="
run_mysql <<SQL
START TRANSACTION;

DELETE FROM orders
WHERE session_id = ${SESSION_ID};

UPDATE seats
SET status = 'available',
    locked_by = NULL,
    locked_until = NULL,
    order_id = NULL,
    order_no = NULL,
    updated_at = NOW()
WHERE session_id = ${SESSION_ID};

UPDATE sessions
SET available_seats = (SELECT COUNT(*) FROM seats WHERE session_id = ${SESSION_ID}),
    sold_seats = 0,
    locked_seats = 0,
    status = 'on_sale',
    updated_at = NOW()
WHERE id = ${SESSION_ID};

COMMIT;
SQL

echo "== MYSQL SESSION SNAPSHOT =="
run_mysql -N -e "SELECT id, session_name, available_seats, sold_seats, locked_seats, status FROM sessions WHERE id = ${SESSION_ID};"

echo "== REDIS/SECKILL INIT =="
curl -X POST "${BASE_URL}/api/seckill/init" \
  -H 'Content-Type: application/json' \
  --data @"${PAYLOAD_PATH}"
echo

echo "== GATE RESET =="
curl -X POST "${BASE_URL}/internal/seckill/gate/reset" \
  -H 'Content-Type: application/json' \
  -d "{\"sessionId\":${SESSION_ID}}"
echo
