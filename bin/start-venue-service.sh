#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

run_service_foreground "taopiaopiao-venue-service/taopiaopiao-venue-service-application"
