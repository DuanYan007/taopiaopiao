#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

run_service_foreground "taopiaopiao-user-service/taopiaopiao-user-service-application"
