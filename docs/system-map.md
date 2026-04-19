# System Map

## Scope
This repository is the backend for a ticketing system built as Spring Boot microservices. The main interview scenario is high-concurrency seat selection for a hot session.

## Core Modules
- `taopiaopiao-gateway`: unified entry, routes `/seckill/**` and `/client/**`, port `8080`.
- `taopiaopiao-seckill-service`: lock seat, manage Redis seat state and Redis lock-order aggregates, port `8086`.
- `taopiaopiao-order-service`: consume `LOCK_ACCEPTED`, create formal unpaid orders, and handle timeout / paid / cancel convergence, port `8087`.
- `taopiaopiao-session-service`: session and seat data, port `8084`.
- `taopiaopiao-user-service`, `venue-service`, `event-service`, `seat-template-service`: supporting read/write services for the rest of the domain.

## Shared Infrastructure
- MySQL: business persistence, database `taopiaopiao`.
- Redis: seat lock state, lock-order aggregates, processing cache, and Lua scripts in `taopiaopiao-common-redis/src/main/resources/lua`.
- RocketMQ: `LOCK_ACCEPTED`, `ORDER_CREATED_INTERNAL`, `ORDER_PAID`, and timeout / cancel events.
- Nacos: service discovery.
- OpenResty: frontend entry and traffic gate at `/usr/local/openresty/nginx`.

## Important Runtime Paths
- OpenResty route config: `/usr/local/openresty/nginx/conf/app.conf`
- OpenResty Lua gate: `/usr/local/openresty/nginx/lua/seckill_gate.lua`
- Frontend source repo: `/home/duanyan/project/taopiaopiao-frontend`

## Current Hot Path
For `sessionId=1`, requests enter OpenResty first, then pass to gateway, `seckill-service`, `order-service`, payment, and MQ consumers. This path is the main place for performance work, consistency review, and pressure testing.
