#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

if [[ -n "${PAYMENT_DIR:-}" ]]; then
    PAYMENT_DIR="${PAYMENT_DIR}"
elif [[ -f "${PWD}/taopiaopiao-payment-system/pom.xml" ]]; then
    PAYMENT_DIR="${PWD}/taopiaopiao-payment-system"
else
    PAYMENT_DIR="${ROOT_DIR}/taopiaopiao-payment-system"
fi

PAYMENT_POM="${PAYMENT_DIR}/pom.xml"
PAYMENT_LOG="${LOG_DIR}/payment-system.log"
PAYMENT_PID_FILE="${RUN_DIR}/payment-system.pid"

if [[ ! -f "${PAYMENT_POM}" ]]; then
    echo "file not found: ${PAYMENT_POM}"
    exit 1
fi

if [[ -f "${PAYMENT_PID_FILE}" ]]; then
    payment_pid="$(cat "${PAYMENT_PID_FILE}")"
    if kill -0 "${payment_pid}" 2>/dev/null; then
        echo "payment-system already started, pid=${payment_pid}"
        exit 0
    fi
    rm -f "${PAYMENT_PID_FILE}"
fi

nohup bash -lc "cd '${PAYMENT_DIR}' && '${MVN_BIN}' spring-boot:run -DskipTests" >"${PAYMENT_LOG}" 2>&1 &
payment_pid=$!
echo "${payment_pid}" >"${PAYMENT_PID_FILE}"
sleep 5

if kill -0 "${payment_pid}" 2>/dev/null; then
    echo "payment-system started, pid=${payment_pid}, log=${PAYMENT_LOG}"
    exit 0
fi

echo "payment-system failed to start, log=${PAYMENT_LOG}"
rm -f "${PAYMENT_PID_FILE}"
exit 1
