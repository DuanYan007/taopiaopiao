# 锁座下单支付全流程说明

这份文档只描述当前代码对应的真实架构，不包含历史方案。

目标是让你快速回答下面几个问题：

- 用户点击“锁座”后，系统同步做了什么，异步做了什么
- 为什么锁座接口可以很快返回
- 正式订单是什么时候创建的
- `payUrl` 为什么不是一开始就一定有
- 支付成功、超时取消、用户取消分别如何收敛
- Redis、MySQL、RocketMQ、OpenResty 各自承担什么职责
- 当前链路的状态机、消息、缓存、表结构是什么

---

## 1. 总体目标

当前架构的核心目标只有一个：

`把“高并发热点锁座”从“正式订单创建 / 支付准备 / MySQL写入”里剥离出来`

具体做法是：

- OpenResty 先在最前面做热点流量闸门
- `seckill-service` 在 Redis 中原子完成锁座受理
- 锁座接口同步返回 `lockId + orderNo + PROCESSING + NOT_READY`
- 正式订单创建改成 MQ 异步
- 支付记录改成 `order-service` 查询订单详情时按需补齐
- 最终由 MQ 消费者和恢复任务把 Redis、MySQL、支付状态收敛到一致

所以当前链路不是“锁座接口直接下单并直接返回支付链接”，而是：

`锁座受理 -> 异步建单 -> 轮询订单 -> 支付准备就绪 -> 用户支付 -> 最终状态收敛`

---

## 2. 参与组件与职责

### 2.1 OpenResty

入口文件：

- [/usr/local/openresty/nginx/conf/app.conf](/usr/local/openresty/nginx/conf/app.conf)
- [/usr/local/openresty/nginx/lua/seckill_gate.lua](/usr/local/openresty/nginx/lua/seckill_gate.lua)

职责：

- 只拦截 `/api/seckill/lock`
- 对热点场次做短时重复点击拦截
- 对热点场次做 token bucket 限流
- 对热点场次做并发闸门 `max_inflight`
- 给请求补 `X-Request-Id`
- 解析上游响应，根据业务码给“成功 / 重复 / 座位不可用”做短时本地缓存

当前默认只对 `sessionId=1` 有静态热点配置：

- `token_rate=100`
- `bucket_capacity=150`
- `max_inflight=40`
- `queue_timeout_ms=80`

并且支持本机接口动态调参：

- `/internal/seckill/gate/status`
- `/internal/seckill/gate/config`
- `/internal/seckill/gate/reset`

### 2.2 gateway

职责：

- 统一转发 `/seckill/**`、`/client/**`

### 2.3 seckill-service

职责：

- Redis 热点锁座受理
- Redis 锁单聚合管理
- Redis 场次快照校验 `eventId`
- Redis 座位状态释放与支付确认
- 异步桥接 `LOCK_ACCEPTED`
- 锁单恢复与 MySQL 审计刷盘

### 2.4 order-service

职责：

- 消费 `LOCK_ACCEPTED`
- 通过 RocketMQ 事务消息异步创建正式订单
- 提供 `/client/orders/{orderNo}` 查询与轮询响应
- 按需创建 mock payment
- 事务回查支付状态
- 超时点最终裁决 `PAID / TIMEOUT`
- 用户取消订单

### 2.5 payment-system

职责：

- 只是模拟支付系统
- 提供创建支付单、查询支付状态、模拟支付成功

### 2.6 Redis

职责：

- 热路径座位状态源
- 锁单聚合源
- 订单处理中缓存
- 过期索引
- Redis Stream 临时事件源

### 2.7 MySQL

职责：

- 正式订单持久化
- `lock_orders` 锁单审计持久化
- `seat_locks` 座位锁审计持久化

### 2.8 RocketMQ

职责：

- 解耦锁座受理与正式建单
- 解耦订单创建与秒杀侧锁单状态推进
- 解耦支付成功、超时取消与座位收敛

---

## 3. 当前核心接口与入口

### 3.1 锁座入口

- 前端请求：`POST /api/seckill/lock`
- OpenResty 转发：`/seckill/lock`
- Java 控制器读取：
  - `X-User-Id`
  - `X-Request-Id`

### 3.2 订单轮询入口

- 前端轮询：`GET /api/client/orders/{orderNo}`

这是当前支付确认页的核心入口。

前端不会依赖锁座返回时就一定有 `payUrl`，而是靠这个接口轮询。

### 3.3 场次缓存初始化入口

`seckill-service` 依赖 Redis 中的 `sessionId -> eventId` 快照。

所以在压测或本地运行前，场次缓存初始化必须完成，否则锁座会因为快照缺失直接失败。

---

## 4. 关键状态机

### 4.1 锁单状态 `LockOrderStatus`

定义文件：

- [LockOrderStatus.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-domain/src/main/java/com/duanyan/taopiaopiao/seckillservice/domain/enums/LockOrderStatus.java)

状态：

- `1 LOCKED`：Redis 锁座已成功
- `2 ORDER_CREATING`：已桥接到 MQ，正在异步建单
- `3 ORDER_CREATED`：正式订单已创建
- `4 PAID`：支付成功
- `5 TIMEOUT`：超时取消
- `6 CANCELLED`：用户取消
- `7 FAILED`：失败

### 4.2 座位锁审计状态 `LockStatus`

定义文件：

- [LockStatus.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-domain/src/main/java/com/duanyan/taopiaopiao/seckillservice/domain/enums/LockStatus.java)

状态：

- `0 RELEASED`
- `1 LOCKED`
- `2 PAID`
- `3 EXPIRED`

### 4.3 正式订单状态 `OrderStatus`

定义文件：

- [OrderStatus.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-domain/src/main/java/com/duanyan/taopiaopiao/orderservice/domain/enums/OrderStatus.java)

状态：

- `0 PROCESSING`
- `1 UNPAID`
- `2 PAID`
- `3 CANCELLED`
- `4 REFUNDED`
- `5 TIMEOUT`

说明：

- `PROCESSING` 只用于前端轮询视图，不是 `orders` 表里的正式订单状态
- 正式订单落库后，初始状态是 `UNPAID`

### 4.4 前端支付状态 `paymentStatus`

当前前端与 `OrderResponse` 约定的字符串：

- `NOT_READY`：支付信息未就绪，继续轮询
- `READY`：已有支付链接，可以支付
- `SUCCESS`：已支付
- `NOT_AVAILABLE`：订单已终态，不需要继续支付

---

## 5. Redis 数据结构

定义文件：

- [RedisKey.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/java/com/duanyan/taopiaopiao/common/redis/constants/RedisKey.java)

### 5.1 座位相关

- `seat:state:{sessionId}:{seatId}`
  - 座位最终状态
  - `0/可选`、`2/已售`
- `seat:lock:{sessionId}:{seatId}`
  - 临时锁
  - 值格式：`userId|lockId`
- `price:{sessionId}:{seatId}`
  - 座位价格

### 5.2 场次快照

- `session:{sessionId}:meta`
  - 至少包含 `eventId`

这是锁座时校验前端 `eventId` 的唯一真相来源。

### 5.3 订单处理中缓存

- `order:processing:{orderNo}`

作用：

- 锁座成功后立即写入
- 正式订单尚未落库时，前端轮询先从这里拿“处理中”态

### 5.4 锁单聚合

- `lock:order:{orderNo}`

内容包括：

- `lockId`
- `orderNo`
- `requestId`
- `userId`
- `sessionId`
- `eventId`
- `seatIdsJson`
- `seatCount`
- `unitPrice`
- `totalAmount`
- `status`
- `paymentStatus`
- `failReason`
- `expireTimeMillis`
- `createdAtMillis`
- `updatedAtMillis`

这就是当前 Redis 侧的“锁单聚合真相”。

### 5.5 用户锁索引

- `lock:user:{sessionId}:{userId}`

作用：

- 同一用户在同一场次下，未终态时不能再持有新的锁单

### 5.6 锁单过期索引

- `lock:expire:{sessionId}`

类型：

- `ZSET`

score：

- `expireTimeMillis`

member：

- `orderNo`

作用：

- `RedisLockOrderRecoveryTask` 按这个索引扫描超时未收敛锁单

### 5.7 锁座受理 Stream

- `stream:lock_accepted:{sessionId}`

作用：

- 锁座 Lua 成功后，把 `payloadJson` 写入 Stream
- `RedisLockAcceptedBridgeTask` 从这里消费，再转发到 RocketMQ

---

## 6. MySQL 持久化结构

### 6.1 正式订单表 `orders`

实体：

- [Order.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-domain/src/main/java/com/duanyan/taopiaopiao/orderservice/domain/entity/Order.java)

关键点：

- `seat_ids` 是 `JSON` 数组
- 正式订单只在事务监听器中创建
- 初始状态是 `UNPAID`

### 6.2 锁单审计表 `lock_orders`

实体：

- [LockOrder.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-domain/src/main/java/com/duanyan/taopiaopiao/seckillservice/domain/entity/LockOrder.java)

关键点：

- 不是热路径真相源
- 是 Redis 锁单聚合的异步刷盘审计表
- `seat_ids_json` 也是 JSON 数组

### 6.3 座位锁审计表 `seat_locks`

实体：

- [SeatLock.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-domain/src/main/java/com/duanyan/taopiaopiao/seckillservice/domain/entity/SeatLock.java)

关键点：

- 保存的是 `seatId = seats.id` 的字符串值
- 行列号在刷盘时再去 `seats` 表回填
- 也是审计表，不是热路径读源

---

## 7. RocketMQ 消息与标签

定义文件：

- [MqTopic.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-mq/src/main/java/com/duanyan/taopiaopiao/common/mq/constant/MqTopic.java)

当前主链路只需要记住下面这些 tag：

- `LOCK_ACCEPTED`
- `ORDER_CREATED_INTERNAL`
- `ORDER_PAID`
- `TIMEOUT_CHECK`
- `CANCEL_ORDER`

### 7.1 消息体

- [LockAcceptedMessage.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-mq/src/main/java/com/duanyan/taopiaopiao/common/mq/message/LockAcceptedMessage.java)
- [OrderCreatedInternalMessage.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-mq/src/main/java/com/duanyan/taopiaopiao/common/mq/message/OrderCreatedInternalMessage.java)
- [OrderPaidMessage.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-mq/src/main/java/com/duanyan/taopiaopiao/common/mq/message/OrderPaidMessage.java)
- [OrderCancelMessage.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-mq/src/main/java/com/duanyan/taopiaopiao/common/mq/message/OrderCancelMessage.java)

---

## 8. 全流程总览

当前全流程可以分成 8 段：

1. 场次缓存初始化
2. OpenResty 前置流量保护
3. Redis 原子锁座受理
4. Redis Stream -> RocketMQ 桥接
5. order-service 异步正式建单
6. 前端轮询订单并按需准备支付
7. 支付成功收敛
8. 超时取消 / 用户取消 / 恢复补偿

下面逐段展开。

---

## 9. 场次缓存初始化阶段

入口：

- `SeckillServiceImpl.initSession(...)`

做的事情：

1. 校验 `SessionInitRequest`
2. 把座位按区域分组
3. 构建 `layout meta`
4. 清理旧 Redis 场次数据
5. 批量写入：
   - 座位状态
   - 座位价格
   - 场次布局缓存
   - `session:{sessionId}:meta -> eventId`

重要结论：

- 锁座链路不再依赖运行时 RPC 去查场次拿 `eventId`
- 前端传来的 `eventId` 只是候选值
- 真正校验依赖 Redis 中的 `sessionId -> eventId` 快照

---

## 10. OpenResty 前置流量保护

入口：

- `location = /api/seckill/lock`

处理顺序：

1. 校验 `X-User-Id`
2. 解析请求体 JSON
3. 校验 `sessionId`
4. 校验 `seatIds`
5. 生成 `seat_fingerprint`
6. 查 terminal hold
7. 查 unavailable hold
8. 查 dedupe 短时重复提交
9. 进入 inflight + token bucket 获取流程
10. 放行到 gateway

### 10.1 terminal hold

作用：

- 对同一个用户、同一个场次、同一组座位的“已成功 / 已重复购票”请求做短时终态记忆

来源：

- OpenResty 在解析上游响应后，看到业务成功或重复购票业务码时写入

### 10.2 unavailable hold

作用：

- 对刚刚被判定“座位不可用”的座位组合做短时间屏蔽

### 10.3 dedupe

作用：

- 防止非常短时间内的重复点击同一请求

### 10.4 inflight + token

作用：

- 不让过多请求同时打进 Java 服务
- 不让突发流量超过系统可接受吞吐

### 10.5 上游响应码回写本地状态

`body_filter_by_lua` 会解析 Java 响应体里的业务码：

- `0`：标记 success hold
- `3`：标记 duplicate hold
- `2`：标记 unavailable hold
- `1`：记 missing seat metric
- 其他：记 other metric

这就是为什么 OpenResty 既是前置闸门，也是短时结果记忆层。

---

## 11. Redis 原子锁座受理

入口：

- `SeckillServiceImpl.lockSeats(...)`

同步流程：

1. 读取请求：
   - `sessionId`
   - `seatIds`
   - `unitPrice`
   - `expireSeconds`
2. 生成：
   - `lockId`
   - `orderNo`
   - `orderExpireTime`
3. 用 Redis 场次快照校验 `eventId`
4. 批量取座位价格并算 `totalAmount`
5. 组装 `LockAcceptedMessage`
6. 调用 Redis Lua `lock_seat_and_record_order.lua`
7. Lua 成功后写 processing cache
8. 同步返回前端

### 11.1 Lua 脚本做了什么

脚本：

- [lock_seat_and_record_order.lua](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/lock_seat_and_record_order.lua)

它一次性做完：

1. 校验 `session meta` 中 `eventId`
2. 校验用户是否已有未终态锁单
3. 校验每个座位：
   - 是否存在
   - 是否已售
   - 是否已被别人临时锁住
4. 所有校验通过后，给每个座位写 `seat:lock`
5. 写 `lock:user:{sessionId}:{userId}`
6. 写 `lock:order:{orderNo}` 聚合
7. 写 `lock:expire:{sessionId}` 过期索引
8. 写 `stream:lock_accepted:{sessionId}`

### 11.2 同步返回给前端的内容

成功时返回：

- `success=true`
- `code=0`
- `lockId`
- `orderNo`
- `expireTime`
- `orderStatus=PROCESSING`
- `paymentStatus=NOT_READY`
- `nextPollMs=1200`
- `nextAction=POLL_ORDER`

注意：

- 这个阶段还没有正式订单
- 这个阶段通常也没有 `payUrl`

### 11.3 失败业务码

`SeckillServiceImpl.lockSeats(...)` 当前业务码：

- `0`：成功
- `1`：座位不存在
- `2`：座位已被锁定或售出
- `3`：用户已锁定或购买该座位
- `4`：场次与演出信息不匹配
- `8`：系统异常

---

## 12. processing cache 的意义

键：

- `order:processing:{orderNo}`

写入时机：

- 锁座受理成功后立刻写

作用：

- 当前端刚跳到支付确认页时，即使正式订单尚未创建，也能先看到一个稳定的“处理中”响应
- 减少前端在正式订单尚未落库时出现“订单不存在”的空窗

删除时机：

- 锁单推进到 `ORDER_CREATED` / `PAID` 等阶段后清理

---

## 13. Redis Stream -> RocketMQ 桥接

任务：

- [RedisLockAcceptedBridgeTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockAcceptedBridgeTask.java)

周期：

- 每 1 秒

做的事情：

1. 找所有已初始化场次
2. 确保 Redis Stream consumer group 存在
3. 读取新消息
4. auto-claim 长时间 pending 的旧消息
5. 解析 `payloadJson`
6. 发送 `LOCK_ACCEPTED` 到 RocketMQ
7. 把 Redis 锁单状态推进到 `ORDER_CREATING`
8. ACK Stream 消息

为什么需要这层桥：

- 锁座热路径先只写 Redis，完全不依赖 RocketMQ 是否瞬时可用
- 后面再由桥接任务平滑地把受理事件送入 MQ

---

## 14. order-service 异步正式建单

消费者：

- [LockAcceptedConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/LockAcceptedConsumer.java)

流程：

1. 收到 `LOCK_ACCEPTED`
2. 按 `orderNo` 查 `orders`
3. 如果正式订单已存在：
   - 直接发 `ORDER_CREATED_INTERNAL`
   - 跳过重复建单
4. 如果锁单已经过期：
   - 直接发 `CANCEL_ORDER(TIMEOUT)`
5. 否则组装 `FormalOrderCreateRequest`
6. 发送 RocketMQ 事务半消息

这里关键点是：

`LOCK_ACCEPTED` 不直接写 orders，而是转成“建单事务半消息”`

---

## 15. RocketMQ 事务监听器建单

监听器：

- [OrderTransactionListener.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/listener/OrderTransactionListener.java)

这个监听器分两个阶段：

### 15.1 executeLocalTransaction

做的事情：

1. 从消息里拿 `orderNo`
2. 幂等检查：正式订单是否已存在
3. 如果不存在，插入 `orders`
   - 状态 `UNPAID`
   - `seat_ids` 写 JSON 数组
4. 发 `ORDER_CREATED_INTERNAL`
5. 发延时 `TIMEOUT_CHECK`
6. 返回 `UNKNOWN`

为什么返回 `UNKNOWN`：

- 因为这里并不意味着订单已经支付
- 只是正式订单已创建
- 后面要靠事务回查确定是否提交 `ORDER_PAID`

### 15.2 checkLocalTransaction

做的事情：

1. 查正式订单是否存在
2. 如果订单已经 `PAID`：
   - 回滚半消息
   - 因为支付成功事件可能已由兜底链路发出
3. 如果订单已经进入取消 / 超时 / 退款终态：
   - 回滚半消息
4. 如果订单仍是 `UNPAID`：
   - 查支付系统状态
5. 根据支付状态决定：
   - `SUCCESS` -> `COMMIT`
   - `NOT_FOUND` -> `UNKNOWN`
   - `PENDING` -> `UNKNOWN`
   - `FAILED/CANCELLED` -> `ROLLBACK`

这就是当前“为什么还用事务消息”的根本原因：

- 用户支付是外部不可控事件
- 需要用 RocketMQ 事务回查在支付窗口内持续判断是否能提交 `ORDER_PAID`

---

## 16. `ORDER_CREATED_INTERNAL` 的作用

生产者：

- [OrderCreatedInternalProducer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/producer/OrderCreatedInternalProducer.java)

消费者：

- [OrderCreatedInternalConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCreatedInternalConsumer.java)

作用非常单纯：

- 正式订单创建后，通知 `seckill-service`
- 把 Redis 锁单聚合状态推进到 `ORDER_CREATED`
- 清理 processing cache

这是秒杀侧和订单侧之间的“正式订单已存在”同步点。

---

## 17. 前端轮询订单详情

接口实现：

- `OrderServiceImpl.getOrderByNo(...)`

查询顺序非常重要：

1. 先查正式 `orders`
2. 没有的话查 `order:processing:{orderNo}`
3. 再查 Redis `lock:order:{orderNo}`
4. 最后调用 `seckill-service` 内部兜底接口

为什么要这样查：

- 正式订单可能还没创建好
- 但用户页面已经拿到了 `orderNo`
- 所以前端必须能在“订单未落库”的窗口里拿到可解释的状态

### 17.1 返回处理中的视图

如果还没正式订单，会返回一个 `OrderResponse` 风格的处理中对象：

- `status=PROCESSING`
- `statusDesc=处理中`
- `paymentStatus=NOT_READY`
- `nextPollMs` 根据过期时间计算

### 17.2 状态映射

Redis 锁单状态映射到前端订单视图时：

- `LOCKED/ORDER_CREATING/ORDER_CREATED` -> `PROCESSING`
- `PAID` -> `PAID`
- `TIMEOUT` -> `TIMEOUT`
- `CANCELLED` -> `CANCELLED`

---

## 18. 支付准备为什么是“懒创建”

逻辑：

- `OrderServiceImpl.enrichPaymentInfo(...)`

当前设计不是锁座时立刻创建支付单，而是：

1. 正式订单存在且状态是 `UNPAID`
2. 查询支付系统
3. 如果支付记录是 `PENDING`
   - 返回 `paymentStatus=READY`
   - 返回 `payUrl`
4. 如果支付记录不存在
   - 调用 payment-system 创建支付记录
   - 创建成功后返回 `READY + payUrl`
5. 如果支付系统暂时异常
   - 返回 `NOT_READY`
   - 前端继续轮询

这样做的目的：

- 锁座成功瞬间不做支付系统写入
- 把支付创建从热点同步路径挪到订单查询路径
- 降低高并发时被 mock payment 拖死的概率

---

## 19. 支付成功链路

### 19.1 事务消息提交 `ORDER_PAID`

一旦 `OrderTransactionListener.checkLocalTransaction(...)` 发现支付成功：

- RocketMQ 提交事务消息
- 下游真正收到 `ORDER_PAID`

### 19.2 order-service 消费 `ORDER_PAID`

消费者：

- [OrderPaidConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderPaidConsumer.java)

职责：

- 只负责把正式订单从 `UNPAID` 更新到 `PAID`
- 已支付则直接幂等返回
- 非 `UNPAID` 终态不强行覆盖

### 19.3 seckill-service 消费 `ORDER_PAID`

消费者：

- [OrderPaidConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderPaidConsumer.java)

职责：

1. 读取 `seatIds`
2. 先检查 `seat_locks` 是否已全部是 `PAID`
3. 调用 Redis `confirmPurchase(...)`
   - 把座位最终状态写为已售
   - 删除临时锁
4. 把 `seat_locks` 标为 `PAID`
5. 把 Redis 锁单聚合推进到 `PAID`
6. 清理 processing cache

因此支付成功后，最终会同时收敛：

- `orders -> PAID`
- Redis 座位状态 -> 已售
- `seat_locks -> PAID`
- `lock_orders / Redis lock order -> PAID`

---

## 20. 超时点最终裁决

消费者：

- [OrderTimeoutCheckConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java)

它消费的是延时 `TIMEOUT_CHECK`。

这是当前链路里另一个非常关键的设计点：

`事务回查负责支付窗口内持续判断；TIMEOUT_CHECK 负责超时点的一次最终裁决`

流程：

1. 查正式订单
2. 如果订单已终态，直接跳过
3. 查支付系统
4. 如果已支付：
   - 把正式订单更新为 `PAID`
   - 补发普通 `ORDER_PAID`
5. 如果未支付：
   - 把正式订单更新为 `TIMEOUT`
   - 发送 `CANCEL_ORDER(TIMEOUT)`

为什么还要补发普通 `ORDER_PAID`：

- 因为事务消息的提交时机和消费者收敛不一定永远完美对齐
- 超时点如果才发现已支付，需要补一次“支付成功事件”给下游完成最终状态收敛

---

## 21. 用户取消链路

入口：

- `OrderServiceImpl.cancelOrder(...)`

流程：

1. 查正式订单
2. 只允许 `UNPAID` 取消
3. 先把本地订单更新为 `CANCELLED`
4. 再发送 `CANCEL_ORDER(USER)`

消费者：

- [OrderCancelConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCancelConsumer.java)

收到取消消息后：

1. 根据 `reason` 判断释放类型
   - `TIMEOUT` -> `EXPIRED`
   - `USER` -> `RELEASED`
2. 调用 `seckillService.releaseSeats(...)`
   - 删除 Redis 临时锁
   - 更新 `seat_locks`
3. 调用 `markLockOrderReleased(...)`
   - Redis 锁单聚合推进到 `TIMEOUT` / `CANCELLED`
   - MySQL `lock_orders` 同步推进
   - 清理 processing cache

---

## 22. Redis 锁单恢复任务

任务：

- [RedisLockOrderRecoveryTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockOrderRecoveryTask.java)

作用：

它是整个链路的补偿保险。

扫描对象：

- `lock:expire:{sessionId}` 的 `orderNo`

处理逻辑：

1. 读 Redis 锁单聚合
2. 如果锁单已终态或已 `ORDER_CREATED`
   - 从过期索引里移除
3. 如果锁单状态不是 `LOCKED/ORDER_CREATING`
   - 不处理
4. 如果状态还不够“陈旧”
   - 不处理
5. 调用 order-service 内部接口确认正式订单是否存在
6. 如果正式订单已存在：
   - 推进 Redis 锁单到 `ORDER_CREATED`
   - 从过期索引移除
7. 如果正式订单不存在，且锁单已过期：
   - 释放 Redis 座位
   - 推进锁单到 `TIMEOUT`

它解决的是：

- `LOCK_ACCEPTED` 发出后，正式订单已创建但 Redis 锁单没推进成功
- Redis 锁单一直卡在 `LOCKED/ORDER_CREATING`
- 正式订单根本没创建出来但座位一直锁着

---

## 23. 异步刷盘任务

### 23.1 RedisLockOrderFlushTask

任务：

- [RedisLockOrderFlushTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockOrderFlushTask.java)

作用：

- 把 Redis `lock:order:{orderNo}` 异步 upsert 到 MySQL `lock_orders`

### 23.2 RedisSeatLockFlushTask

任务：

- [RedisSeatLockFlushTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisSeatLockFlushTask.java)

作用：

- 把 Redis 锁单聚合拆成 `seat_locks` 审计记录

注意点：

- 首次刷盘时，会去 `seats` 表查 `seat_row / seat_column`
- 所以当前 `seat_locks` 行列号不再依赖 `row:col` 形式的 seatId

---

## 24. 幂等与防重设计

### 24.1 OpenResty 防重

- dedupe key
- terminal hold
- unavailable hold
- inflight gate
- token bucket

### 24.2 Redis Lua 防重

- 同一用户同场次已有锁单，直接拒绝
- 座位已售或被别人锁，直接拒绝

### 24.3 MQ 消费者幂等

- `LockAcceptedConsumer` 先查正式订单是否已存在
- `OrderPaidConsumer(order-service)` 已支付直接跳过
- `OrderPaidConsumer(seckill-service)` 先看 `seat_locks` 是否已全支付
- `OrderCancelConsumer` 按状态更新，不反复覆盖

### 24.4 状态推进幂等

Redis 锁单状态推进用：

- `transitionLockOrderStatus(orderNo, expectedStatuses, targetStatus, ...)`

只有当前状态在允许集合里才推进，避免乱序覆盖。

---

## 25. 前端为什么不会“找不到订单”

因为 `GET /api/client/orders/{orderNo}` 背后做了多级兜底：

1. 正式订单
2. processing cache
3. Redis 锁单聚合
4. seckill 内部兜底接口

所以支付确认页只要拿到 `orderNo`，就可以安全轮询。

它可能看到：

- `PROCESSING + NOT_READY`
- `UNPAID + READY + payUrl`
- `PAID + SUCCESS`
- `TIMEOUT/CANCELLED + NOT_AVAILABLE`

---

## 26. 监控与压测观察点

任务：

- [SeckillBacklogSnapshotTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/SeckillBacklogSnapshotTask.java)
- [SeckillFlowMetrics.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/monitor/SeckillFlowMetrics.java)

会记录：

- `lockOrdersLockedCount`
- `lockOrdersOrderCreatingCount`
- `lockOrdersOrderCreatedCount`
- 最老卡住锁单的年龄
- Redis 过期队列数量
- Redis Stream 大小
- Stream pending 数量
- bridge 发送成功/失败次数
- recovery 超时释放次数
- recovery 命中“正式订单已存在”补收敛次数

OpenResty 还会提供：

- gate status
- allow/reject/upstream 各类计数

压测时要同时看：

- OpenResty `tpp_access.log`
- seckill-service 日志
- order-service 日志
- `seckill-backlog-snapshot`

---

## 27. 当前链路最关键的设计结论

如果只记 10 句话，记下面这些：

1. 锁座接口当前只做“Redis 受理”，不做同步正式建单。
2. 真正的 `eventId` 校验来自 Redis 的场次快照，不来自运行时 RPC。
3. `seatIds` 当前语义是 `seats.id` 的字符串数组。
4. `lock:order:{orderNo}` 是 Redis 侧锁单聚合真相源。
5. `lock_orders` 和 `seat_locks` 都是异步刷盘审计，不是热路径真相源。
6. 正式订单只在 `OrderTransactionListener.executeLocalTransaction()` 中创建。
7. `ORDER_PAID` 只表示“支付已经真实成功”，不是“订单已创建”。
8. `TIMEOUT_CHECK` 是超时点最终裁决，和事务回查不是一回事。
9. `payUrl` 是 `order-service` 在查询订单详情时懒创建 / 懒补齐的，不保证锁座成功就立刻有。
10. 整个系统的策略是“高并发优先，最终一致性收敛”，不是跨服务强一致。

---

## 28. 一条完整样例时间线

假设用户在前端选中 `sessionId=1` 的座位 `["4"]`：

1. 前端发 `POST /api/seckill/lock`
2. OpenResty 校验、限流、放行，并生成 `X-Request-Id`
3. `seckill-service` 用 Redis Lua 原子写入：
   - `seat:lock:1:4`
   - `lock:user:1:{userId}`
   - `lock:order:{orderNo}`
   - `lock:expire:1`
   - `stream:lock_accepted:1`
4. 接口马上返回：
   - `lockId`
   - `orderNo`
   - `PROCESSING`
   - `NOT_READY`
5. 前端跳转 `order-confirm.html?orderNo=...`
6. 前端开始轮询 `/api/client/orders/{orderNo}`
7. 同时 `RedisLockAcceptedBridgeTask` 把 Stream 消息转成 RocketMQ `LOCK_ACCEPTED`
8. `order-service` 收到 `LOCK_ACCEPTED`
9. 发送建单事务半消息
10. 事务监听器本地插入 `orders(status=UNPAID)`
11. 事务监听器发 `ORDER_CREATED_INTERNAL`
12. `seckill-service` 把 Redis 锁单推进到 `ORDER_CREATED`
13. 前端轮询命中正式订单
14. `order-service` 查询 payment-system，发现没支付记录，于是创建支付记录
15. `/client/orders/{orderNo}` 返回：
   - `paymentStatus=READY`
   - `payUrl`
16. 前端启用支付按钮
17. 用户点击支付，跳去 payment-system
18. 支付成功后，事务回查命中 `SUCCESS`
19. RocketMQ 提交 `ORDER_PAID`
20. `order-service` 更新 `orders -> PAID`
21. `seckill-service` 把 Redis 座位状态改成已售，并把 `seat_locks`、`lock_orders` 推进到 `PAID`
22. 前端轮询再查订单，得到：
   - `status=PAID`
   - `paymentStatus=SUCCESS`
   - `nextPollMs=0`

这就是当前真实全链路。
