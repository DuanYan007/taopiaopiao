#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

ROCKETMQ_DIR="/home/duanyan/rocketmq-all-5.4.0-bin-release"
NAMESRV_BIN="${ROCKETMQ_DIR}/bin/mqnamesrv"
BROKER_BIN="${ROCKETMQ_DIR}/bin/mqbroker"

NAMESRV_LOG="${LOG_DIR}/rocketmq-namesrv.log"
BROKER_LOG="${LOG_DIR}/rocketmq-broker.log"

NAMESRV_PID_FILE="${RUN_DIR}/rocketmq-namesrv.pid"
BROKER_PID_FILE="${RUN_DIR}/rocketmq-broker.pid"

require_file() {
    local file_path="$1"
    if [[ ! -f "${file_path}" ]]; then
        echo "file not found: ${file_path}"
        exit 1
    fi
}

read_pid() {
    local pid_file="$1"
    if [[ -f "${pid_file}" ]]; then
        cat "${pid_file}"
    fi
}

is_running() {
    local pid_file="$1"
    local pid
    pid="$(read_pid "${pid_file}")"
    if [[ -z "${pid}" ]]; then
        return 1
    fi

    if kill -0 "${pid}" 2>/dev/null; then
        return 0
    fi

    rm -f "${pid_file}"
    return 1
}

start_process() {
    local service_name="$1"
    local binary_path="$2"
    local log_file="$3"
    local pid_file="$4"

    if is_running "${pid_file}"; then
        echo "${service_name} already started, pid=$(read_pid "${pid_file}")"
        return 0
    fi

    nohup "${binary_path}" >"${log_file}" 2>&1 &
    local pid=$!
    echo "${pid}" >"${pid_file}"
    sleep 5

    if kill -0 "${pid}" 2>/dev/null; then
        echo "${service_name} started, pid=${pid}, log=${log_file}"
        return 0
    fi

    echo "${service_name} failed to start, log=${log_file}"
    rm -f "${pid_file}"
    exit 1
}

require_file "${NAMESRV_BIN}"
require_file "${BROKER_BIN}"

start_process "rocketmq-namesrv" "${NAMESRV_BIN}" "${NAMESRV_LOG}" "${NAMESRV_PID_FILE}"
start_process "rocketmq-broker" "${BROKER_BIN}" "${BROKER_LOG}" "${BROKER_PID_FILE}"
