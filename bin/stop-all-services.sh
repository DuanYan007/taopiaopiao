#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

stop_service_by_pid_file "gateway"
stop_service_by_pid_file "seckill-service"
stop_service_by_pid_file "order-service"
stop_service_by_pid_file "seat-template-service"
stop_service_by_pid_file "session-service"
stop_service_by_pid_file "event-service"
stop_service_by_pid_file "venue-service"
stop_service_by_pid_file "user-service"
