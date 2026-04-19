# Local Runbook

## Local Dependencies
- MySQL on `localhost:3306`, database `taopiaopiao`, username/password `root/7566`
- Redis on `localhost:6349`, password `7566`
- Nacos on `localhost:8848`
- RocketMQ name server on `127.0.0.1:9876`
- OpenResty at `/usr/local/openresty/nginx`
- MySQL and Redis are expected to be started by Docker containers in local development

## Recommended Start Order
1. Start MySQL, Redis, Nacos, RocketMQ, and the payment/mock dependencies.
2. Start `session-service`, `event-service`, `venue-service`, `seat-template-service`, `user-service`.
3. Start `seckill-service`, `order-service`, then `gateway`.
4. Reload OpenResty if route or Lua changes were made.

## Local Environment Convention
- The current local development convention is Docker-based infrastructure plus locally started Java services.
- Keep stable, non-sensitive development facts in versioned docs.
- Keep machine-specific secrets such as `sudo` password out of versioned files.
- If you need machine-only notes, create `docs/local-private.md` from `docs/local-private.example.md`.

## Useful Commands
```bash
mvn -q -DskipTests compile
mvn -pl taopiaopiao-seckill-service/taopiaopiao-seckill-service-application spring-boot:run
mvn -pl taopiaopiao-order-service/taopiaopiao-order-service-application spring-boot:run
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/hotspot_throughput.js
```

## Pressure Test Notes
- Current real pressure-test target is `sessionId=1`.
- OpenResty gate is only enabled for this hotspot session.
- Watch `/usr/local/openresty/nginx/logs/tpp_access.log`, `seckill-service` logs, `order-service` logs, and system resource usage during tests.
- Before pressure tests, ensure the session cache is initialized with both `sessionId` and `eventId`; lock-seat hot path now depends on the Redis session snapshot instead of a runtime `session-service` RPC.

## OpenResty Gate Tuning
Use these localhost-only endpoints after OpenResty reload:

```bash
curl http://127.0.0.1/internal/seckill/gate/status?sessionId=1

curl -X POST http://127.0.0.1/internal/seckill/gate/config \
  -H 'Content-Type: application/json' \
  -d '{
    "sessionId": 1,
    "token_rate": 120,
    "bucket_capacity": 180,
    "max_inflight": 48,
    "queue_timeout_ms": 100
  }'

curl -X POST http://127.0.0.1/internal/seckill/gate/reset \
  -H 'Content-Type: application/json' \
  -d '{"sessionId":1}'
```

- `status` 会返回当前生效配置、运行中 `inflight/tokens`、以及 `allow/reject/upstream_*` 计数。
- `config` 支持局部更新；未传的字段保留当前 override 值。
- `reset` 会清除该场次的 override、token/inflight 运行态和计数器，适合每轮压测前做一次。

## Lock Only Burst Example
```bash
docker run --rm --network host \
  -e SESSION_ID=2 \
  -e USERS=1600 \
  -e USER_BASE=100000 \
  -e SEAT_START_ID=161 \
  -e SEAT_END_ID=1760 \
  -e SEATS_PER_REQUEST=1 \
  -e MAX_ATTEMPTS_PER_USER=30 \
  -e REQUEST_INTERVAL_SECONDS=1 \
  -e MAX_DURATION=60s \
  -v "$(pwd)/scripts/loadtest:/scripts" \
  grafana/k6 run /scripts/lock_only_burst.js
```

- `SEAT_START_ID`、`SEAT_END_ID`、`SEATS_PER_REQUEST` 会直接决定实际抢的座位池；脚本现在会按这三个参数动态生成目标座位，不再固定只打前 20 个座位。
- 默认命中 `409/429` 后仍会继续重试，直到成功、命中业务终态失败，或达到 `MAX_ATTEMPTS_PER_USER`。如果要让用户在命中闸门拒绝时直接停止，可加 `-e STOP_ON_409=true` 或 `-e STOP_ON_429=true`。

## Verification Checklist
- `nginx -t` passes after OpenResty edits.
- Seat lock returns `lockId`, `orderNo`, `expireTime`, `orderStatus=PROCESSING`, and `paymentStatus=NOT_READY`.
- Frontend or manual polling of `/client/orders/{orderNo}` can obtain `paymentStatus=READY` and `payUrl`.
- Paid event updates order and sold-seat state.
- Timeout or user cancel releases unpaid locked seats.
