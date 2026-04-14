#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

cd "${ROOT_DIR}"
"${MVN_BIN}" -q -DskipTests install

start_service_background "session-service" "taopiaopiao-session-service/taopiaopiao-session-service-application"
start_service_background "order-service" "taopiaopiao-order-service/taopiaopiao-order-service-application"
start_service_background "seckill-service" "taopiaopiao-seckill-service/taopiaopiao-seckill-service-application"
start_service_background "gateway" "taopiaopiao-gateway"
