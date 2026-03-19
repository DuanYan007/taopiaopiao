# 项目状态 - 淘票票后端项目

## 项目基本信息

**项目名称**: 淘票票后端 (TaoPiaoPiao Backend)

**架构模式**: 微服务架构（DDD四层→三层简化）

**最后更新**: 2026-03-19

---

## 技术栈

### 核心框架
- **Java 版本**: Java 17
- **Spring Boot**: 3.2.4
- **Maven**: 3.9.x

### Spring Cloud
- **Spring Cloud**: 2023.0.1
- **Spring Cloud Alibaba**: 2023.0.1.0
- **Nacos**: 服务发现与配置中心
- **OpenFeign**: 4.1.0（服务间调用）
- **Loadbalancer**: 4.1.0（客户端负载均衡）

### 持久层与数据库
- **ORM**: MyBatis-Plus 3.5.7
- **数据库**: MySQL Server 8.4.8
- **连接池**: Druid 1.2.23
- **MySQL Connector**: mysql-connector-j 9.1.0

### 工具库
- **Hutool**: 5.8.32
- **Commons Lang3**: 3.17.0
- **Commons IO**: 2.18.0

### 安全与认证
- **JWT**: jjwt 0.12.6

### 缓存与分布式
- **Redis**: Redisson（Redis 端口 6349，密码 7566）
- **Lua 脚本**: lock_seat.lua, unlock_seat.lua, confirm_purchase.lua

### API文档
- **Knife4j**: 4.5.0 (OpenAPI 3.0)

---

## 项目结构

```
taopiaopiao-backend/
├── taopiaopiao-common/              # 公共核心模块
├── taopiaopiao-common-web/          # 公共Web模块
├── taopiaopiao-common-redis/        # 公共Redis模块（Lua脚本、RedisService）
│   ├── lua/
│   │   ├── lock_seat.lua           # 锁座脚本
│   │   ├── unlock_seat.lua         # 释放座位脚本
│   │   └── confirm_purchase.lua    # 确认购买脚本
│   └── constants/
│       ├── SeatStatus.java         # 座位状态枚举（0=可选，1=已锁定，2=已售出）
│       └── RedisKey.java           # Redis Key 常量
├── taopiaopiao-gateway/             # 网关服务
├── taopiaopiao-user-service/        # 用户服务 (8081)
├── taopiaopiao-venue-service/       # 场馆服务 (8082)
├── taopiaopiao-event-service/       # 演出服务 (8083)
├── taopiaopiao-session-service/     # 场次服务 (8084)
├── taopiaopiao-seat-template-service/  # 座位模板服务 (8085)
├── taopiaopiao-seckill-service/     # 秒杀/选座服务 (8086)
│   ├── taopiaopiao-seckill-service-api/
│   │   └── dto/
│   │       ├── LockSeatRequest.java
│   │       └── LockSeatResponse.java
│   ├── taopiaopiao-seckill-service-application/
│   │   ├── client/                 # Feign Client（OrderClient, SessionClient）
│   │   ├── controller/
│   │   │   ├── SeckillController.java      # 对外接口
│   │   │   └── InternalSeatController.java # 内部接口
│   │   ├── service/impl/
│   │   │   └── SeckillServiceImpl.java
│   │   └── mapper/
│   │       └── SeatLockMapper.java
│   └── taopiaopiao-seckill-service-domain/
│       ├── entity/
│       │   └── SeatLock.java       # 座位锁定记录表
│       └── enums/
│           └── LockStatus.java     # 0=已释放，1=已锁定，2=已支付
└── taopiaopiao-order-service/       # 订单服务 (8087)
    ├── taopiaopiao-order-service-api/
    │   └── dto/
    │       ├── CreateOrderRequest.java
    │       ├── OrderResponse.java
    │       └── OrderPageRequest.java
    ├── taopiaopiao-order-service-application/
    │   ├── client/                 # Feign Client（SeckillInternalClient, SessionClient, EventClient, VenueClient, SeatTemplateClient）
    │   ├── controller/
    │   │   ├── OrderController.java          # 对外接口
    │   │   └── InternalOrderController.java # 内部接口
    │   ├── service/impl/
    │   │   └── OrderServiceImpl.java
    │   ├── config/
    │   │   └── OrderIdGenerator.java # 简化版雪花算法
    │   └── mapper/
    │       └── OrderMapper.java
    └── taopiaopiao-order-service-domain/
        ├── entity/
        │   └── Order.java
        └── enums/
            └── OrderStatus.java     # 1=未支付，2=已支付，3=已取消，4=已退款，5=超时取消
```

---

## 已完成功能（Done）

### 基础设施
- [x] Maven 多模块项目搭建
- [x] Spring Boot 3.2.4 集成
- [x] MyBatis-Plus 集成
- [x] Druid 数据源配置
- [x] MySQL 8.4.8 连接
- [x] Knife4j API 文档
- [x] 统一响应格式 `Result<T>`
- [x] 全局异常处理器
- [ BusinessException 自定义异常
- [x] 常量管理 Constants
- [x] MyBatis-Plus 自动填充（createdAt, updatedAt）
- [x] Nacos 服务发现集成
- [x] OpenFeign 服务间调用集成
- [x] 客户端负载均衡 Loadbalancer

### 用户服务（user-service）
- [x] 管理员用户 CRUD
- [x] JWT Token 生成与验证
- [x] 登录功能

### 场馆服务（venue-service）
- [x] 场馆 CRUD（分页查询）
- [x] 场馆状态管理
- [x] 客户端查询接口（/client/venues）

### 演出服务（event-service）
- [x] 演出 CRUD（分页查询）
- [x] 演出状态管理
- [x] 票档管理（TicketTier）
- [x] 客户端查询接口（/client/events）

### 场次服务（session-service）
- [x] 场次 CRUD（分页查询）
- [x] 场次状态管理
- [x] 通过 OpenFeign 调用 venue-service 和 event-service
- [x] 场次查询时自动填充 `eventName` 和 `venueName`
- [x] 客户端查询接口（/client/sessions）

### 网关服务（gateway）
- [x] Spring Cloud Gateway 搭建

### 座位模板服务（seat-template-service）
- [x] 座位模板 CRUD（分页查询）
- [x] 座位模板状态管理
- [x] 按场馆查询模板
- [x] 客户端查询接口（/client/seat-templates）
- [x] 通过 Feign 调用 venue-service 获取场馆名称

### 秒杀服务（seckill-service）
- [x] Redis + Lua 原子性锁座（防止超卖）
- [x] 座位状态管理（可选、已锁定、已售出）
- [x] 锁座记录表 seat_locks
- [x] 释放座位接口（取消/超时订单）
- [x] 标记座位已支付接口
- [x] 锁座失败自动回滚机制

### 订单服务（order-service）
- [x] 创建待支付订单（锁座成功后自动创建）
- [x] 支付订单（确认购买）
- [x] 订单查询（分页、详情）
- [x] 取消订单（释放座位）
- [x] 删除订单
- [x] 超时订单处理（定时任务）
- [x] 雪花算法订单号生成

### Redis 基础设施
- [x] common-redis 模块创建
- [x] Redisson 客户端集成
- [x] Lua 脚本加载与执行
- [x] 座位状态初始化接口
- [x] 场次数据清理接口

---

## 进行中（In Progress）

无

---

## 待开发（Todo）

### 抢票/秒杀业务域增强
- [ ] RocketMQ 消息队列集成（异步处理、削峰填谷）
- [ ] 抢票资格预约（预热机制）
- [ ] 真实支付集成（支付宝/微信）
- [ ] 分布式事务优化（Saga/TCC）
- [ ] 座位状态持久化（定时同步 Redis→MySQL）

### 性能优化
- [ ] 多级缓存（本地缓存 Caffeine + Redis）
- [ ] 数据库读写分离
- [ ] 接口限流（Sentinel）
- [ ] 座席数据冷热分离

### 其他
- [ ] 前端对接联调
- [ ] 通知服务（购票成功/失败通知）

---

## 数据库配置

### 连接信息
- **MySQL 端口**: 3306
- **地址**: localhost
- **数据库**: taopiaopiao
- **用户**: root
- **密码**: 7566

### Redis 配置
- **端口**: 6349
- **地址**: localhost
- **密码**: 7566

### 服务端口
- **Gateway**: 8080
- **User Service**: 8081
- **Venue Service**: 8082
- **Event Service**: 8083
- **Session Service**: 8084
- **Seat Template Service**: 8085
- **Seckill Service**: 8086
- **Order Service**: 8087

### API文档
- **Knife4j UI**: http://localhost:8080/doc.html
- **Druid 监控**: http://localhost:8082/druid/

---

## 架构决策（Decisions）

### 1. 微服务拆分
- 按业务领域拆分：user, venue, event, session
- 每个服务包含 api, application, domain 三层
- common 和 common-web 作为公共模块

### 2. 统一响应格式
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {},
  "timestamp": 1234567890
}
```

### 3. OpenFeign 调用方式
- Client 接口返回 `Result<T>` 类型
- Service 层通过 `resp.getData()` 获取实体对象
- 不使用自定义 Decoder，让 Feign 默认处理

### 4. 数据库连接
- MySQL Server 8.4.8 使用 mysql-connector-j 9.1.0
- JDBC URL: `jdbc:mysql://localhost:3306/taopiaopiao?serverTimezone=Asia/Shanghai`

### 5. MyBatis-Plus
- 分页插件已配置
- 自动填充：createdAt（插入时），updatedAt（插入和更新时）
- 逻辑删除：deleted 字段（0=未删除，1=已删除）

---

## 错误日志（Error Log）

### 2026-02-12 - Gateway Spring MVC/WebFlux 冲突
**错误**: Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway

**原因**: common-web 模块包含 spring-boot-starter-web，被 Gateway 依赖引入

**解决方案**: 拆分 common 模块为 common（无Web依赖）和 common-web（含Web依赖），Gateway 只依赖 common

### 2026-02-12 - 循环依赖错误
**错误**: 创建 Feign Decoder 配置导致循环依赖

**解决方案**: 不创建自定义 Decoder，直接在 Service 层处理 `Result<T>` 类型

### 2026-02-12 - 更新时间 updatedAt 未更新
**原因**: 从数据库查询出的实体对象有旧值，自动填充只在字段为 null 时生效

**解决方案**: 在更新前手动设置 `entity.setUpdatedAt(null)`

### 禁止模式
1. ❌ 擅自修改 DDL 或数据库结构
2. ❌ 假设 AUTO_INCREMENT ID 的值
3. ❌ Gateway 中引入 Spring MVC 依赖
4. ❌ 创建会导致循环依赖的 Feign Decoder
5. ❌ 跨服务直接 import 其他服务的 DTO
6. ❌ 新增微服务后忘记配置网关路由

---

## 当前工作状态

**最近更新**: 2026-03-19

**当前任务**: 秒杀服务和订单服务已完成

**核心实现**:
- **锁座机制**: Redis + Lua 原子性操作，防止超卖
- **座位状态**: 0=可选，1=已锁定，2=已售出
- **订单流程**: 锁座 → 创建待支付订单 → 支付 → 确认购买 → 标记座位已售出
- **超时处理**: 15分钟过期，定时任务取消超时订单
