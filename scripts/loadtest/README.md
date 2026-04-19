# Load Test Scripts

These k6 scripts target the current gated lock-seat entry at `sessionId=1`.

## Prerequisites

Start OpenResty, gateway, `seckill-service`, `order-service`, `session-service`, Redis, MySQL, RocketMQ, Nacos, and the mock payment service. Make sure `sessionId=1` has been initialized with enough available seats.

## Run with Docker

From the repository root:

```bash
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/repeat_click.js
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/hotspot_conflict.js
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/hotspot_throughput.js
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

## Observe During Test

- OpenResty access log: `/usr/local/openresty/nginx/logs/tpp_access.log`
- `seckill-service` logs for actual backend entries
- `order-service` logs for downstream formal order creation and payment preparation
- `docker stats` or `top` for host resource usage
