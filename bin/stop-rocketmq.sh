#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

stop_service_by_pid_file "rocketmq-broker"
stop_service_by_pid_file "rocketmq-namesrv"
stop_service_by_port "rocketmq-broker" 10911
stop_service_by_port "rocketmq-namesrv" 9876
