# TaoPiaoPiao

Microservice backend for a ticketing system focused on high-concurrency seat locking, formal order creation, payment confirmation, timeout/cancel convergence, and eventual consistency.

## What This Repo Is

- Java 17 Maven multi-module Spring Boot backend.
- Core path: `OpenResty -> gateway -> seckill-service -> order-service -> payment/MQ consumers`.
- Redis is the hot-path truth for seat locks and lock-order aggregates.
- MySQL `orders` is the durable source for formal order state.
- RocketMQ carries accepted-lock, order-created, paid, timeout, and cancel convergence events.
- Frontend source is outside this repo at `/home/duanyan/project/taopiaopiao-frontend`.

## Main Modules

- `taopiaopiao-gateway`: unified gateway, port `8080`.
- `taopiaopiao-seckill-service`: seat-lock acceptance, Redis Lua, lock-order aggregate, recovery tasks, port `8086`.
- `taopiaopiao-order-service`: formal orders, payment preparation, timeout adjudication, port `8087`.
- `taopiaopiao-session-service`: session and seat data, port `8084`.
- `taopiaopiao-user-service`, `taopiaopiao-venue-service`, `taopiaopiao-event-service`, `taopiaopiao-seat-template-service`: supporting services.
- `taopiaopiao-common*`: shared response, web, Redis, OSS, and MQ support.

## Runtime Entry Points

- OpenResty root: `/usr/local/openresty/nginx`
- OpenResty route config: `/usr/local/openresty/nginx/conf/app.conf`
- OpenResty gate Lua: `/usr/local/openresty/nginx/lua/seckill_gate.lua`
- Deployed frontend assets: `/usr/local/openresty/nginx/html`
- Load-test scripts: `scripts/loadtest/`
- Local component inventory: `conf/local-components.yml`

OpenResty gate logic applies to all valid `sessionId`; `sessionId=1` has an extra static hotspot override. Current local `lock_only_burst` tuning uses `sessionId=2`.

## Local Startup

Start infrastructure first:

- MySQL on `localhost:3306`, database `taopiaopiao`
- Redis on `localhost:6349`
- Nacos on `localhost:8848`
- RocketMQ name server on `127.0.0.1:9876`
- OpenResty
- mock payment service

Compile:

```bash
mvn -q -DskipTests compile
```

Run selected services:

```bash
mvn -pl taopiaopiao-seckill-service/taopiaopiao-seckill-service-application spring-boot:run
mvn -pl taopiaopiao-order-service/taopiaopiao-order-service-application spring-boot:run
mvn -pl taopiaopiao-gateway spring-boot:run
```

For load testing, use [scripts/loadtest/README.md](/home/duanyan/project/taopiaopiao-backend/scripts/loadtest/README.md).

## Repository Context

- [AGENTS.md](/home/duanyan/project/taopiaopiao-backend/AGENTS.md): repository rules and collaboration guardrails.
- [conf/README.md](/home/duanyan/project/taopiaopiao-backend/conf/README.md): local component configuration index.
- [stable-context.md](/home/duanyan/.codex/memories/taopiaopiao-backend/stable-context.md): stable Codex project memory.
- [active-context.md](/home/duanyan/.codex/memories/taopiaopiao-backend/active-context.md): dated recent project context.
- `.codex/skills/`: repository-specific Codex workflows.

Keep durable project facts in memory, not in expanding docs. Update memory with `tpp-refresh-project-memory` when architecture, workflow, or default project assumptions change.
