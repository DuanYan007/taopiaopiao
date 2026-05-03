# Load Test Scripts

These k6 scripts target the current gated lock-seat entry.

Current real in-project pressure-test sessions:

- `sessionId=2` -> `1600座位测试场`
- `sessionId=3` -> `16000-测试`

## Prerequisites

Start OpenResty, gateway, `seckill-service`, `order-service`, `session-service`, Redis, MySQL, RocketMQ, Nacos, and the mock payment service. Use the existing seeded sessions in the project instead of creating ad hoc test sessions.

## Run with Docker

From the repository root:

```bash
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/repeat_click.js
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/hotspot_conflict.js
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/hotspot_throughput.js
```

For `lock_only_burst.js`, prefer the repository runners so the full console output and `STATUS0_SAMPLE` lines are saved automatically:

```bash
bash bin/run-lock-burst-1600.sh
bash bin/run-lock-burst-16000.sh
```

Before each `lock_only_burst` round, use `reset -> config -> run` in this order. `reset` clears the session override as well as runtime counters.

## Recommended current entry points

### 1600-seat session

Full reset:

```bash
MYSQL_PASSWORD=your-mysql-password bash bin/reset-loadtest-1600.sh
```

Fixed runner:

```bash
bash bin/run-lock-burst-1600.sh
```

Session facts:

- `sessionId=2`
- session name: `1600座位测试场`
- seat id range: `161-1760`

### 16000-seat session

Full reset:

```bash
MYSQL_PASSWORD=your-mysql-password bash bin/reset-loadtest-16000.sh
```

Fixed runner:

```bash
bash bin/run-lock-burst-16000.sh
```

Session facts:

- `sessionId=3`
- session name: `16000-测试`
- seat id range: `1761-17760`

## Current recommended local profile for `sessionId=2`

```bash
MYSQL_PASSWORD=your-mysql-password bash bin/reset-loadtest-1600.sh
```

```bash
curl -X POST http://127.0.0.1/internal/seckill/gate/config -H 'Content-Type: application/json' -d '{"sessionId":2,"token_rate":420,"bucket_capacity":620,"max_inflight":160,"queue_timeout_ms":260}'
```

```bash
bash bin/run-lock-burst-1600.sh
```

Verified result for this profile under the current local `1600` users / `1600` seats burst:
- `200=1600`
- `429=998`
- `status_0=0`
- `http_req_duration p95≈302.89ms`

## Suggested profile for first 16000-seat round

Do not jump directly to the absolute maximum. Start with a real in-project large session but ramp conservatively:

```bash
MYSQL_PASSWORD=your-mysql-password bash bin/reset-loadtest-16000.sh
```

```bash
curl -X POST http://127.0.0.1/internal/seckill/gate/config -H 'Content-Type: application/json' -d '{"sessionId":3,"token_rate":1200,"bucket_capacity":1800,"max_inflight":320,"queue_timeout_ms":400}'
```

```bash
USERS=4000 MAX_DURATION=60s bash bin/run-lock-burst-16000.sh
```

## Optional Environment Variables

```bash
-e BASE_URL=http://127.0.0.1/api/seckill/lock
-e SESSION_ID=1
-e UNIT_PRICE=1280
-e VUS=50
-e DURATION=60s
-e SLEEP_SECONDS=0.2
```

Example:

```bash
docker run --rm --network host \
  -e VUS=100 \
  -e DURATION=90s \
  -v "$(pwd)/scripts/loadtest:/scripts" \
  grafana/k6 run /scripts/hotspot_conflict.js
```

## Scenarios

- `repeat_click.js`: same user, same seats, validates short-window duplicate rejection.
- `hotspot_conflict.js`: different users, same seats, validates hotspot contention and `409/429` behavior.
- `hotspot_throughput.js`: different users, rotating seats, validates gated throughput and backend stability.
- `lock_only_burst.js`: rotating-seat burst test used for the current session-gate tuning loop.
- `bin/reset-loadtest-1600.sh`: fixed full reset for the real `1600座位测试场`
- `bin/reset-loadtest-16000.sh`: fixed full reset for the real `16000-测试`
- `bin/run-lock-burst-1600.sh`: fixed runner for the real `1600座位测试场`
- `bin/run-lock-burst-16000.sh`: fixed runner for the real `16000-测试`

## Observe During Test

- OpenResty access log: `/usr/local/openresty/nginx/logs/tpp_access.log`
- `seckill-service` logs for actual backend entries
- `order-service` logs for downstream formal order creation and payment preparation
- `docker stats` or `top` for host resource usage

## Metric Interpretation

- `status_200_total` means the lock API returned HTTP `200`. It does not distinguish business success from business conflict in the response body.
- `status_409_total` and `status_429_total` mean the request got a real HTTP response with those statuses.
- `status_other_total` means the request was not `200/409/429`. Use the split metrics below to see which class it belongs to.
- `status_0_total` means k6 did not receive a normal HTTP response. These requests typically will not appear in OpenResty access logs.
- `transport_failure_total` counts only `status_0` transport-layer failures and tags them by `error` and `error_code`, so you can separate timeout, connection reset, DNS, and similar client-side failures from server responses.
- `status_unexpected_4xx_total` means the server returned a real but unexpected `4xx` status other than `409/429`.
- `status_5xx_total` means the server returned a real `5xx` response.
- `status_unexpected_other_total` is a fallback bucket for anything else that was not covered above.
- `lock_only_burst.js` prints a compact custom summary at the end of the run, including a `Transport Failure Breakdown` section that lists up to the top 8 observed `error/error_code` combinations directly.
- `lock_only_burst.js` also emits `STATUS0_SAMPLE` lines during the run. Each line contains `requestId`, `userId`, `attempt`, `error`, `error_code`, and `seatIds`, and the runner script writes them into `logs/loadtest/lock-only-burst-*.log`.
