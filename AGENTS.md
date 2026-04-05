# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven multi-module Spring Boot project for a microservice-based ticketing backend. The root `pom.xml` aggregates shared modules (`taopiaopiao-common*`) and services such as `taopiaopiao-user-service`, `taopiaopiao-order-service`, `taopiaopiao-seckill-service`, and `taopiaopiao-gateway`. Each service is split into `*-api`, `*-domain`, and `*-application` modules. Java sources live under `src/main/java`, MyBatis XML files under `src/main/resources/mapper`, SQL bootstrap scripts in `sql/`, and architecture notes in `docs/`.

Frontend delivery is served by OpenResty from `/usr/local/openresty/nginx`. Route definitions are maintained in `/usr/local/openresty/nginx/conf/app.conf`, and deployed frontend assets are served from `/usr/local/openresty/nginx/html`. The main frontend source repository is `/home/duanyan/project/taopiaopiao-frontend`.

Key working docs live in `docs/`:
- `docs/system-map.md`: service boundaries, ports, and core dependencies.
- `docs/business-flow/seckill.md`: lock-seat, pay, and cancel flow notes.
- `docs/invariants.md`: rules that should not be broken during refactors.
- `docs/runbook-local.md`: local startup, verification, and pressure-test steps.
- `docs/codex-workflow.md`: preferred way to collaborate with Codex on this repo.

## Build, Test, and Development Commands
- `mvn clean install -DskipTests`: build all modules and install artifacts locally.
- `mvn -q -DskipTests compile`: fast compile check across the repository.
- `mvn -pl taopiaopiao-order-service/taopiaopiao-order-service-application spring-boot:run`: run a single service locally. Replace the module path for other services.
- `mvn -pl taopiaopiao-order-service/taopiaopiao-order-service-application test`: run tests for one module when tests are added.

Run infrastructure such as MySQL, Redis, Nacos, and RocketMQ before starting services; most `application.yml` files assume local defaults.

## Coding Style & Naming Conventions
Use Java 17, 4-space indentation, UTF-8, and standard Spring conventions. Keep package names lowercase under `com.duanyan.taopiaopiao`. Prefer `PascalCase` for classes, `camelCase` for methods/fields, and suffix Spring roles clearly, for example `OrderPaidConsumer`, `OrderServiceImpl`, `SeatMapper`. Keep DTOs in `api` or `client.dto`, domain entities in `domain.entity`, and avoid mixing transport objects with persistence models.

## Testing Guidelines
There is currently little to no committed test coverage, so every change should at minimum pass compilation and be validated against the affected flow. Add new tests under `src/test/java` and mirror production package structure. Prefer Spring Boot tests for integration paths such as MQ consumers, Redis seat locking, and order state transitions. Name tests `*Test` and test methods by behavior, for example `shouldSkipTimeoutWhenPaymentSucceeded`.

## Commit & Pull Request Guidelines
Recent history uses short, task-focused commit messages, usually in Chinese, such as `支付链路和订单取消链路优化`. Follow that style: one purpose per commit, concise summary first. PRs should include the affected modules, the business flow changed, manual verification steps, and any SQL or config updates. For message or state-machine changes, include a short sequence description of producer, consumer, and persistence impact.

## Security & Configuration Tips
Do not hardcode new secrets or environment-specific endpoints. Prefer environment variables or config center values over editing `application.yml`. If a change touches payment, MQ, or cancellation flows, verify idempotency and retry behavior before merging.

## Agent Workflow
Before changing code, read the relevant file in `docs/` and then inspect the affected controller, service, consumer, and mapper classes. For high-risk flows such as seat locking, payment, RocketMQ consumers, cancellation, and OpenResty gating, review the full producer-consumer chain before proposing code changes.
