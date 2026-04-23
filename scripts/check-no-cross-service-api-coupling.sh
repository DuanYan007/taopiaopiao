#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failures=0

package_suffix_for_module() {
    case "$1" in
        taopiaopiao-order-service) echo "orderservice" ;;
        taopiaopiao-event-service) echo "eventservice" ;;
        taopiaopiao-user-service) echo "userservice" ;;
        taopiaopiao-session-service) echo "sessionservice" ;;
        taopiaopiao-seckill-service) echo "seckillservice" ;;
        taopiaopiao-venue-service) echo "venueservice" ;;
        taopiaopiao-seat-template-service) echo "seatternplateservice" ;;
        *)
            echo "unknown module: $1" >&2
            return 1
            ;;
    esac
}

echo "Checking application pom dependencies..."
while IFS= read -r pom; do
    module_dir="$(dirname "$pom")"
    own_artifact="$(basename "$module_dir" | sed 's/-application$//')"
    output="$(rg -n "<artifactId>taopiaopiao-.*-service-api</artifactId>" "$pom" | grep -v "$own_artifact-api" || true)"
    if [[ -n "$output" ]]; then
        failures=1
        echo "[FAIL] cross-service api dependency in $pom"
        printf '%s\n' "$output"
    fi
done < <(find taopiaopiao-*-service -path "*/taopiaopiao-*-service-application/pom.xml" | sort)

echo "Checking application source imports..."
while IFS= read -r src_dir; do
    module="$(echo "$src_dir" | cut -d/ -f1)"
    own_package="$(package_suffix_for_module "$module")"
    output="$(rg -n "com\\.duanyan\\.taopiaopiao\\.[^.]+service\\.api(\\.|;)" "$src_dir" | grep -v "com.duanyan.taopiaopiao.${own_package}.api" || true)"
    if [[ -n "$output" ]]; then
        failures=1
        echo "[FAIL] cross-service api usage in $module"
        printf '%s\n' "$output"
    fi
done < <(find taopiaopiao-*-service -path "*/taopiaopiao-*-service-application/src/main/java" | sort)

if [[ "$failures" -ne 0 ]]; then
    echo "Cross-service api coupling detected."
    exit 1
fi

echo "OK: no cross-service api coupling found in application modules."
