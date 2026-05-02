#!/usr/bin/env bash

set -euo pipefail

OPENRESTY_BIN="${OPENRESTY_BIN:-/usr/local/openresty/nginx/sbin/nginx}"
OPENRESTY_PID_FILE="${OPENRESTY_PID_FILE:-/usr/local/openresty/nginx/logs/nginx.pid}"
OPENRESTY_PORT="${OPENRESTY_PORT:-80}"

if [[ ! -x "${OPENRESTY_BIN}" ]]; then
    exit 1
fi

"${OPENRESTY_BIN}" -t >/dev/null 2>&1 || exit 1

if [[ ! -f "${OPENRESTY_PID_FILE}" ]]; then
    exit 1
fi

pid="$(cat "${OPENRESTY_PID_FILE}")"
kill -0 "${pid}" 2>/dev/null || exit 1

ss -ltn "( sport = :${OPENRESTY_PORT} )" 2>/dev/null | grep -q ":${OPENRESTY_PORT}" || exit 1

exit 0
