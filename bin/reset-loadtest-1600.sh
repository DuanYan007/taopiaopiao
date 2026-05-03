#!/usr/bin/env bash

set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${BIN_DIR}/.." && pwd)"

bash "${BIN_DIR}/reset-loadtest-session.sh" \
  2 \
  "${ROOT_DIR}/data/loadtest-1600-session-init.json"
