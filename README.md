# TaoPiaoPiao

淘票票项目的本地开发仓库，包含票务后端微服务、模拟支付系统、OpenResty 部署基线、已构建的前端静态资源，以及压测脚本。

本文档使用可移植写法描述本地目录约定：

- 后端仓库根目录：`<repo-root>`
- OpenResty 运行目录：`/usr/local/openresty/nginx`
- Maven 用户仓库：`$HOME/.m2/repository`

## 1. 项目概览

项目目标是支撑演出/场次/座位的高并发抢票链路，核心关注点是：

- 秒杀入口限流与削峰
- Redis 热路径座位锁定
- 正式订单创建
- 支付状态回查
- 超时关单与取消收敛
- RocketMQ 异步最终一致性

当前本地默认链路：

`Browser -> OpenResty -> gateway -> seckill-service -> order-service -> payment-system / RocketMQ consumers`

其中：

- Redis 是座位锁、锁单聚合、处理中间态的热路径事实来源
- MySQL `taopiaopiao` 是正式业务数据持久化来源
- RocketMQ 承接锁单接受、订单创建、支付成功、超时检查、取消收敛等异步事件
- OpenResty 同时承担前端静态资源入口、API 反向代理、秒杀闸门 Lua 限流

## 2. 仓库内容

本仓库不只是后端代码，还包含联调用的外围材料：

- Java 17 Maven 多模块 Spring Boot 后端
- `taopiaopiao-payment-system` 模拟支付系统
- `deploy/openresty/` OpenResty 部署基线
- `html/` 前端静态页面产物
- `bin/` 本地启动、停止、状态、压测辅助脚本
- `scripts/loadtest/` k6 压测脚本
- `conf/` 本地组件清单和环境变量模板

## 3. 目录结构

```text
taopiaopiao/
├── bin/                              # 本地启动/停止/状态脚本
├── conf/                             # 本地环境配置模板与组件清单
├── deploy/openresty/                 # OpenResty nginx.conf / app.conf / lua
├── html/                             # 前端静态资源（admin/client/assets）
├── scripts/loadtest/                 # k6 压测脚本
├── taopiaopiao-common*               # 公共模块
├── taopiaopiao-user-service          # 用户服务
├── taopiaopiao-venue-service         # 场馆服务
├── taopiaopiao-event-service         # 演出服务
├── taopiaopiao-session-service       # 场次服务
├── taopiaopiao-seat-template-service # 座位模板服务
├── taopiaopiao-seckill-service       # 秒杀/锁座服务
├── taopiaopiao-order-service         # 订单服务
├── taopiaopiao-gateway               # 网关服务
└── taopiaopiao-payment-system        # 模拟支付系统
```

## 4. 后端模块说明

### 4.1 公共模块

- `taopiaopiao-common`
- `taopiaopiao-common-web`
- `taopiaopiao-common-redis`
- `taopiaopiao-common-oss`
- `taopiaopiao-common-mq`

这些模块提供统一响应、Web 公共能力、Redis Lua、OSS、RocketMQ 基础封装等共享能力。

### 4.2 业务服务

| 服务 | 模块 | 默认端口 | 作用 |
| --- | --- | --- | --- |
| gateway | `taopiaopiao-gateway` | `8080` | 统一网关，承接 OpenResty 转发 |
| user-service | `taopiaopiao-user-service/...-application` | `8081` | 用户相关能力 |
| venue-service | `taopiaopiao-venue-service/...-application` | `8082` | 场馆能力 |
| event-service | `taopiaopiao-event-service/...-application` | `8083` | 演出能力 |
| session-service | `taopiaopiao-session-service/...-application` | `8084` | 场次、座位数据 |
| seat-template-service | `taopiaopiao-seat-template-service/...-application` | `8085` | 座位模板管理 |
| seckill-service | `taopiaopiao-seckill-service/...-application` | `8086` | 秒杀入口、Redis Lua 锁座、锁单聚合 |
| order-service | `taopiaopiao-order-service/...-application` | `8087` | 正式订单、支付准备、超时检查、取消收敛 |
| payment-system | `taopiaopiao-payment-system` | `7500` | 模拟支付创建、查询、成功/失败回放 |

### 4.3 服务分层

大部分业务服务采用三层模块结构：

- `*-api`
- `*-domain`
- `*-application`

其中：

- `api` 放接口 DTO / Feign / 对外契约
- `domain` 放领域对象、Mapper、核心业务
- `application` 放 Spring Boot 启动类、配置、控制器、装配

## 5. 前端与 OpenResty

### 5.1 当前前端形态

当前仓库内已经带有前端静态资源，位于：

- `html/admin/`
- `html/client/`
- `html/assets/`
- `html/favicon.ico`

这意味着在本机联调时，不需要额外依赖独立前端仓库即可通过 OpenResty 访问管理端和用户端页面。

### 5.2 OpenResty 作用

OpenResty 在本项目里承担三件事：

1. 前端静态资源入口
2. `/api/` 请求反向代理到 `gateway`
3. `/api/seckill/lock` 秒杀锁座请求的 Lua 闸门限流

### 5.3 OpenResty 关键目录

- 根目录：`/usr/local/openresty/nginx`
- 主配置：`/usr/local/openresty/nginx/conf/nginx.conf`
- 路由配置：`/usr/local/openresty/nginx/conf/app.conf`
- Lua 目录：`/usr/local/openresty/nginx/lua/`
- 静态资源目录：`/usr/local/openresty/nginx/html`
- 访问日志：`/usr/local/openresty/nginx/logs/tpp_access.log`

### 5.4 OpenResty 仓库基线

仓库内对应文件：

- `deploy/openresty/nginx.conf`
- `deploy/openresty/app.conf`
- `deploy/openresty/lua/seckill_gate.lua`
- `deploy/openresty/lua/seckill_gate_util.lua`
- `html/`

迁移到机器时，直接同步到上述 OpenResty 目录即可。

### 5.5 OpenResty 路由说明

当前 `app.conf` 的主要行为：

- `/api/seckill/lock` 先执行 Lua gate，再代理到 `http://gateway_backend/seckill/lock`
- `/api/` 代理到 `http://gateway_backend/`
- `/admin/` 指向 `html/admin/`
- `/client/` 指向 `html/client/`
- `/assets/` 和 `/favicon.ico` 走静态资源
- `/` 301 跳转到 `/admin/`

## 6. 支付系统现状

### 6.1 模块位置

- `taopiaopiao-payment-system/`

### 6.2 当前运行模式

支付系统当前已经调整为内存模式，不依赖 MySQL。

也就是说：

- 业务记录存储在 `ConcurrentHashMap`
- Spring Boot 启动时不再初始化 Druid / DataSource / MyBatis-Plus
- 不需要创建 `payment_db`

当前用途是：

- 创建模拟支付单
- 查询支付状态
- 手工模拟支付成功/失败
- 配合订单服务完成本地联调和压测链路

## 7. 中间件与本地默认配置

### 7.1 MySQL

- Host：`127.0.0.1`
- Port：`3306`
- Database：`taopiaopiao`
- Username：`root`

说明：

- 正式业务服务默认连接 `taopiaopiao`
- 支付系统当前不再需要独立数据库

### 7.2 Redis

- Host：`127.0.0.1`
- Port：`6349`

说明：

- 主要用于秒杀热路径
- 座位锁、锁单聚合、处理中状态、部分缓存均在 Redis 中
- 大压测推荐使用 `scripts/loadtest/redis-loadtest.conf.example` 对应配置

### 7.3 Nacos

- Addr：`127.0.0.1:8848`

说明：

- 本地微服务集成运行时依赖服务注册发现

### 7.4 RocketMQ

- NameServer：`127.0.0.1:9876`
- 本地默认目录：`$HOME/rocketmq-all-5.4.0-bin-release`

说明：

- 承接锁单、订单、支付、超时、取消等异步消息
- 本地脚本 `bin/start-rocketmq.sh` 默认从当前用户家目录读取 RocketMQ 安装目录

### 7.5 OpenResty

- Root：`/usr/local/openresty/nginx`
- Version baseline：`openresty/1.29.2.1`

### 7.6 Maven

- Maven Home：`/usr/share/maven`
- 用户配置：`$HOME/.m2/settings.xml`
- 用户本地仓库：`$HOME/.m2/repository`

当前用户级 Maven 已可独立维护镜像配置，不影响全局设置。

## 8. 关键配置文件

### 8.1 仓库内配置入口

- `conf/local-components.yml`：本地组件清单
- `conf/local-env.example`：环境变量模板
- `deploy/openresty/README.md`：OpenResty 部署说明
- `scripts/loadtest/README.md`：压测入口说明

### 8.2 服务配置

每个应用模块的主配置一般位于：

- `*/src/main/resources/application.yml`

重点关注：

- 数据源
- Redis
- Nacos
- RocketMQ
- 服务端口

## 9. 构建与启动

### 9.1 全量编译

```bash
mvn -q -DskipTests compile
```

### 9.2 全量安装

```bash
mvn clean install -DskipTests
```

### 9.3 单服务启动

示例：

```bash
mvn -pl taopiaopiao-gateway spring-boot:run
mvn -pl taopiaopiao-seckill-service/taopiaopiao-seckill-service-application spring-boot:run
mvn -pl taopiaopiao-order-service/taopiaopiao-order-service-application spring-boot:run
```

### 9.4 使用 bin 脚本启动

核心链路：

```bash
bash bin/start-core-services.sh
```

全量后端：

```bash
bash bin/start-all-services.sh
```

支付系统：

```bash
bash bin/start-payment-system.sh
```

RocketMQ：

```bash
bash bin/start-rocketmq.sh
```

服务状态：

```bash
bash bin/status-services.sh
```

停止服务：

```bash
bash bin/stop-all-services.sh
bash bin/stop-payment-system.sh
bash bin/stop-rocketmq.sh
```

### 9.5 支付系统脚本说明

`bin/start-payment-system.sh` 目前的目录解析顺序是：

1. `PAYMENT_DIR` 环境变量
2. 当前目录下的 `./taopiaopiao-payment-system`
3. 仓库根目录下的 `taopiaopiao-payment-system`

因此，在当前仓库根目录执行脚本即可正常启动支付系统。

## 10. 推荐本地启动顺序

建议按下面顺序启动本地联调环境：

1. MySQL
2. Redis
3. Nacos
4. RocketMQ
5. OpenResty
6. `payment-system`
7. `session-service`
8. `order-service`
9. `seckill-service`
10. `gateway`
11. 其他辅助业务服务（如 `user-service`、`venue-service`、`event-service`、`seat-template-service`）

如果只是验证核心抢票链路，最少可启动：

- OpenResty
- payment-system
- gateway
- seckill-service
- order-service
- session-service
- MySQL
- Redis
- Nacos
- RocketMQ

## 11. 压测入口

压测脚本目录：

- `scripts/loadtest/`

推荐入口：

```bash
bash bin/run-lock-only-burst.sh
```

压测前提：

- OpenResty 已启动
- gateway / seckill-service / order-service / session-service 已启动
- Redis / MySQL / RocketMQ / Nacos / payment-system 已启动
- 目标场次有足够座位数据

当前本地 `lock_only_burst` 推荐基线：

- `sessionId=2`

压测前推荐按 `reset -> config -> run` 顺序执行，详细参数见：

- `scripts/loadtest/README.md`

## 12. 常见联调问题

### 12.1 支付系统报 `Unknown database 'payment_db'`

这表示你运行的是旧配置或旧进程日志。

当前仓库里的支付系统已经切换为内存模式，正常情况下不需要：

- `payment_db`
- Druid 数据源
- MyBatis-Plus 持久化表

如果仍然遇到类似报错，优先检查：

1. 是否拉到了最新本地代码
2. 是否启动的是当前仓库中的 `taopiaopiao-payment-system`
3. 是否还存在旧的支付系统进程
4. `logs/payment-system.log` 是否已经刷新为新的内存模式启动日志

### 12.2 OpenResty 页面能开但接口不通

优先检查：

- `/usr/local/openresty/nginx/conf/app.conf`
- `gateway` 是否监听 `8080`
- OpenResty 是否已 `nginx -t` 并 reload

### 12.3 `status-services.sh` 结果异常

当前脚本已经改为固定读取仓库自己的 `.run/` 目录，因此建议直接使用仓库下的脚本，不要手写 PID 检查逻辑。

## 13. 与仓库相关的说明文件

- `AGENTS.md`：仓库协作规则
- `conf/README.md`：本地组件配置说明
- `conf/local-components.yml`：本地组件清单
- `conf/local-env.example`：环境变量模板
- `deploy/openresty/README.md`：OpenResty 部署说明
- `scripts/loadtest/README.md`：压测说明

如果后续本地目录、默认端口、启动方式、OpenResty 路由、支付系统模式再次变化，README 应优先同步更新。
