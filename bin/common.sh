#!/usr/bin/env bash

set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${BIN_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
RUN_DIR="${ROOT_DIR}/.run"
MVN_BIN="${MVN_BIN:-mvn}"

mkdir -p "${LOG_DIR}" "${RUN_DIR}"

read_listen_pid_by_port() {
    local port="$1"
    ss -ltnp "( sport = :${port} )" 2>/dev/null \
        | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' \
        | head -n 1
}

run_service_foreground() {
    local module_path="$1"
    cd "${ROOT_DIR}/${module_path}"
    exec "${MVN_BIN}" spring-boot:run -DskipTests
}

start_service_background() {
    local service_name="$1"
    local module_path="$2"
    local log_file="${LOG_DIR}/${service_name}.log"
    local pid_file="${RUN_DIR}/${service_name}.pid"

    nohup bash -lc "cd '${ROOT_DIR}/${module_path}' && '${MVN_BIN}' spring-boot:run -DskipTests" >"${log_file}" 2>&1 &
    echo $! >"${pid_file}"
    echo "${service_name} started, pid=$(cat "${pid_file}"), log=${log_file}"
}

stop_service_by_pid_file() {
    local service_name="$1"
    local pid_file="${RUN_DIR}/${service_name}.pid"

    if [[ ! -f "${pid_file}" ]]; then
        echo "${service_name}: pid file not found"
        return 0
    fi

    local pid
    pid="$(cat "${pid_file}")"
    if kill -0 "${pid}" 2>/dev/null; then
        kill "${pid}"
        echo "${service_name}: stopped pid=${pid}"
    else
        echo "${service_name}: process not running"
    fi
    rm -f "${pid_file}"
}

stop_service_by_port() {
    local service_name="$1"
    local port="$2"
    local pid
    pid="$(read_listen_pid_by_port "${port}")"

    if [[ -z "${pid}" ]]; then
        echo "${service_name}: no process listening on port ${port}"
        return 0
    fi

    if kill -0 "${pid}" 2>/dev/null; then
        kill "${pid}"
        echo "${service_name}: stopped pid=${pid} via port ${port}"
        return 0
    fi

    echo "${service_name}: pid=${pid} on port ${port} is not running"
}
