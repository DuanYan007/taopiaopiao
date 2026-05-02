#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1}"
PAYMENT_CHECK_ORDER_NO="${PAYMENT_CHECK_ORDER_NO:-KEEPALIVED_HEALTHCHECK}"
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-2}"
MAX_TIME="${MAX_TIME:-5}"

curl_common=(
    --silent
    --show-error
    --fail
    --connect-timeout "${CONNECT_TIMEOUT}"
    --max-time "${MAX_TIME}"
)

curl "${curl_common[@]}" -I "${BASE_URL}/admin/" >/dev/null
curl "${curl_common[@]}" -I "${BASE_URL}/client/" >/dev/null
curl "${curl_common[@]}" "${BASE_URL}/api/client/sessions" | grep -q '"success":true'
curl "${curl_common[@]}" "${BASE_URL}/payment/query?orderNo=${PAYMENT_CHECK_ORDER_NO}" | grep -q '"success":true'

exit 0
