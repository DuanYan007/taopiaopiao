#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

stop_service "payment-system"
stop_service "gateway"
stop_service "seckill-service"
stop_service "order-service"
stop_service "seat-template-service"
stop_service "session-service"
stop_service "event-service"
stop_service "venue-service"
stop_service "user-service"
