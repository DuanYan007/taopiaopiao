#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

for service in payment-system gateway user-service venue-service event-service session-service seat-template-service seckill-service order-service; do
    pid_file="${RUN_DIR}/${service}.pid"
    if [[ -f "${pid_file}" ]]; then
        pid="$(cat "${pid_file}")"
        if kill -0 "${pid}" 2>/dev/null; then
            echo "${service}: running, pid=${pid}"
            continue
        fi

        rm -f "${pid_file}"

        port="$(service_port "${service}" 2>/dev/null || true)"
        if [[ -n "${port}" ]]; then
            port_pid="$(read_listen_pid_by_port "${port}")"
            if [[ -n "${port_pid}" ]]; then
                echo "${service}: running, pid=${port_pid} (pid file was stale)"
                continue
            fi
        fi

        echo "${service}: not running (removed stale pid=${pid})"
    else
        port="$(service_port "${service}" 2>/dev/null || true)"
        if [[ -n "${port}" ]]; then
            port_pid="$(read_listen_pid_by_port "${port}")"
            if [[ -n "${port_pid}" ]]; then
                echo "${service}: running, pid=${port_pid} (not started by script)"
                continue
            fi
        fi
        echo "${service}: not started by script"
    fi
done
