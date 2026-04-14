#!/usr/bin/env bash

set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DB="${MYSQL_DB:-taopiaopiao}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-7566}"

mysql \
  -h"${MYSQL_HOST}" \
  -P"${MYSQL_PORT}" \
  -u"${MYSQL_USER}" \
  -p"${MYSQL_PASSWORD}" \
  "${MYSQL_DB}" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM seat_locks;
DELETE FROM orders;
SET FOREIGN_KEY_CHECKS = 1;
SQL

echo "Cleared tables: ${MYSQL_DB}.seat_locks, ${MYSQL_DB}.orders"
