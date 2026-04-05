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

## Verification Checklist
- `nginx -t` passes after OpenResty edits.
- Seat lock returns `orderNo` and `payUrl`.
- Paid event updates order and sold-seat state.
- Timeout or user cancel releases unpaid locked seats.
