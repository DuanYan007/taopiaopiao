# TaoPiaoPiao Backend

一个以“演出场次抢票与选座”为核心场景的微服务后端项目，覆盖高并发下的锁座、下单、支付、取消与最终一致性处理流程。

## 项目定位

这个项目不是通用商城，而是围绕单一高并发热点场景做深挖：

- 热门场次抢票与选座
- Redis + Lua 锁座
- RocketMQ 异步事件驱动
- MySQL 持久化订单与锁座记录
- Nacos 服务发现
- OpenResty 前置流量控制与削峰
- 最终一致性而非跨服务强一致性

当前重点链路是 `sessionId=1` 的高并发锁座与支付流程。

## 技术栈

- Java 17
- Spring Boot 3.2.4
- Spring Cloud 2023.0.1
- Spring Cloud Alibaba 2023.0.1.0
- MyBatis-Plus
- MySQL
- Redis
- RocketMQ
- Nacos
- OpenFeign
- OpenResty / Nginx
- k6

## 仓库结构

这是一个 Maven 多模块项目，根 `pom.xml` 统一管理依赖与模块。

### 公共模块

- `taopiaopiao-common`
- `taopiaopiao-common-web`
- `taopiaopiao-common-redis`
- `taopiaopiao-common-oss`
- `taopiaopiao-common-mq`

### 核心服务

- `taopiaopiao-gateway`：统一网关，端口 `8080`
- `taopiaopiao-user-service`：用户服务，端口 `8081`
- `taopiaopiao-venue-service`：场馆服务，端口 `8082`
- `taopiaopiao-event-service`：演出服务，端口 `8083`
- `taopiaopiao-session-service`：场次服务，端口 `8084`
- `taopiaopiao-seat-template-service`：座位模板服务，端口 `8085`
- `taopiaopiao-seckill-service`：锁座/秒杀服务，端口 `8086`
- `taopiaopiao-order-service`：订单服务，端口 `8087`

## 前端与 OpenResty

前端入口由 OpenResty 承担：

- OpenResty 目录：`/usr/local/openresty/nginx`
- 路由配置：`/usr/local/openresty/nginx/conf/app.conf`
- 部署后的前端静态资源：`/usr/local/openresty/nginx/html`
- 前端源码仓库：`/home/duanyan/project/taopiaopiao-frontend`

当前项目在 OpenResty 层对 `/api/seckill/lock` 做了热点场次流量闸门，用于在真正进入 Java 服务前先完成一层削峰保护。

## 核心业务链路

### 1. 锁座链路

1. 用户请求进入 OpenResty
2. 对 `sessionId=1` 应用短时间重复点击拦截、令牌桶限流、并发闸门
3. Gateway 转发到 `seckill-service`
4. `seckill-service` 调用 Redis Lua 原子锁座
5. 锁座成功后写入 `seat_locks`
6. 调用 `order-service` 创建待支付订单并返回 `orderNo`、`payUrl`

### 2. 支付成功链路

1. `order-service` 发送 RocketMQ 事务半消息并创建本地待支付订单
2. 超时前由 Broker 事务回查持续确认支付状态
3. 到支付超时点后，由 `order-service` 的延时超时检查逻辑做最终裁决
4. 若已支付则发出 `ORDER_PAID`，若未支付则发出 `CANCEL_ORDER`
5. 下游消费者分别更新订单、Redis、`seat_locks` 和座位售出状态

### 3. 取消链路

1. 用户主动取消或 `order-service` 的延时超时检查确认未支付
2. `order-service` 更新订单状态为 `CANCELLED` 或 `TIMEOUT`
3. `seckill-service` 释放 Redis 锁座并清理锁座记录

## 一致性设计

本项目明确采用最终一致性，而不是分布式强一致性。

原因是：

- 抢票链路是高并发热点写场景
- 性能优先于跨服务同步阻塞
- 支付、取消、座位状态变更适合拆成多个消费者分别处理
- 幂等、重试、补偿比强行追求同步一致更符合这个项目的业务取舍

## 本地依赖

当前本地开发默认依赖如下：

- MySQL：`localhost:3306`
- 数据库名：`taopiaopiao`
- MySQL 账号密码：`root/7566`
- Redis：`localhost:6349`
- Redis 密码：`7566`
- Nacos：`localhost:8848`
- RocketMQ NameServer：`127.0.0.1:9876`

说明：

- MySQL 与 Redis 默认通过 Docker 容器启动
- Java 服务默认本地启动
- 机器私有信息不应写入版本库

## 快速开始

### 1. 初始化数据库

先创建数据库：

```bash
mysql -uroot -p7566 -e "CREATE DATABASE IF NOT EXISTS taopiaopiao CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

再按需执行 `sql/` 下的脚本。可先查看：

- `sql/README.md`
- `sql/ddl_order.sql`
- `sql/seckill_service.sql`
- 其他 `ddl_*.sql` 与初始化脚本

### 2. 启动基础设施

确保以下组件可用：

- MySQL
- Redis
- Nacos
- RocketMQ
- 支付/Mock 依赖服务
- OpenResty

### 3. 编译项目

```bash
mvn -q -DskipTests compile
```

### 4. 启动服务

建议顺序：

1. `session-service`
2. `event-service`
3. `venue-service`
4. `seat-template-service`
5. `user-service`
6. `seckill-service`
7. `order-service`
8. `gateway`

示例：

```bash
mvn -pl taopiaopiao-seckill-service/taopiaopiao-seckill-service-application spring-boot:run
mvn -pl taopiaopiao-order-service/taopiaopiao-order-service-application spring-boot:run
mvn -pl taopiaopiao-gateway spring-boot:run
```

## 压测

仓库内置了 k6 压测脚本，位于 `scripts/loadtest/`：

- `repeat_click.js`：同用户重复点击
- `hotspot_conflict.js`：多用户争抢同一批座位
- `hotspot_throughput.js`：热点场次吞吐压测

运行方式：

```bash
docker run --rm --network host -v "$(pwd)/scripts/loadtest:/scripts" grafana/k6 run /scripts/hotspot_throughput.js
```

压测时建议同时观察：

- `/usr/local/openresty/nginx/logs/tpp_access.log`
- `seckill-service` 日志
- `order-service` 日志
- `docker stats`
- 主机 CPU / 内存

## 重要文档

- `docs/system-map.md`：项目地图
- `docs/business-flow/seckill.md`：抢票主链路
- `docs/invariants.md`：不能破坏的业务约束
- `docs/runbook-local.md`：本地运行说明
- `docs/codex-workflow.md`：如何配合 Codex 开发
- `docs/skill-standard.md`：仓库内 skill 统一规范
