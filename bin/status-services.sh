#!/usr/bin/env bash

set -euo pipefail

for service in gateway user-service venue-service event-service session-service seat-template-service seckill-service order-service; do
    pid_file=".run/${service}.pid"
    if [[ -f "${pid_file}" ]]; then
        pid="$(cat "${pid_file}")"
        if kill -0 "${pid}" 2>/dev/null; then
            echo "${service}: running, pid=${pid}"
        else
            echo "${service}: stale pid=${pid}"
        fi
    else
        echo "${service}: not started by script"
    fi
done
