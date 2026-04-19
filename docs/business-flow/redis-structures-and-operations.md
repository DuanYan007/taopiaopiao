# Redis 数据结构与链路操作说明

这份文档只描述当前代码中的 Redis 真实用法，目标是回答 4 个问题：

- Redis 里到底有哪些 key 和数据结构
- 每个结构在链路哪个阶段被读写
- 具体执行了什么操作
- 每个结构在当前架构里的职责是什么

说明：

- 这里只统计 Redis，不包含 OpenResty `lua_shared_dict` 本地共享内存。
- 文档按“在用热链路优先，保留但未走热链路的 key 单独说明”组织。

---

## 1. Redis 在当前架构中的定位

Redis 不是简单缓存，而是当前锁座链路的核心状态源，承担 5 类职责：

1. 场次快照与座位静态数据承载。
2. 热路径座位状态判断与临时锁控制。
3. 锁单聚合状态承载。
4. 锁座受理后的异步桥接与恢复索引承载。
5. 订单处理中短期轮询缓存承载。

其中最关键的一点是：

- `seat:state:*` 只保存最终态。
- “已锁定”不是单独写入状态码，而是通过 `seat:lock:*` 是否存在动态推导出来。

也就是说，当前座位有效状态优先级是：

- 已售出 `2`
- 已锁定 `1`
- 可选 `0`

---

## 2. Redis Key 全量清单

| Key 模式                             | 数据结构                  | 主要写入阶段     | 主要读取阶段                    | 作用                                        |         |
| ---------------------------------- | --------------------- | ---------- | ------------------------- | ----------------------------------------- | ------- |
| `seat:state:{sessionId}:{seatId}`  | String                | 场次初始化、支付确认 | 锁座校验、座位图刷新                | 保存座位最终状态，当前只用 `0/2`                       |         |
| `seat:lock:{sessionId}:{seatId}`   | String                | 锁座受理       | 锁座校验、座位图刷新、释放、支付确认        | 保存座位临时锁归属，值为 `userId                      | lockId` |
| `price:{sessionId}:{seatId}`       | String                | 场次初始化      | 锁座受理前金额计算                 | 保存单座位价格                                   |         |
| `session:{sessionId}:meta`         | Hash/Map              | 场次初始化      | 锁座校验、桥接任务扫描、恢复任务扫描、观测任务扫描 | 保存场次快照元数据，当前至少包含 `sessionId`、`eventId`    |         |
| `session:layout:{sessionId}`       | Hash/Map              | 场次初始化      | 前端拉取座位图                   | 保存布局 JSON，字段为 `meta`、`area:0`、`area:1`... |         |
| `order:processing:{orderNo}`       | String/Bucket，值为序列化对象 | 锁座成功后      | 前端轮询订单                    | 订单正式落库前的短期处理中缓存                           |         |
| `lock:order:{orderNo}`             | Hash/Map              | 锁座受理       | 前端轮询、内部查询、刷盘、恢复、支付/取消状态推进 | Redis 锁单聚合主记录                             |         |
| `lock:user:{sessionId}:{userId}`   | String                | 锁座受理       | 锁座 Lua 校验                 | 限制同一用户在同一场次存在未终态锁单                        |         |
| `lock:expire:{sessionId}`          | ZSET                  | 锁座受理       | 恢复任务、观测任务、终态清理            | 锁单过期索引，score 为 `expireTimeMillis`         |         |
| `stream:lock_accepted:{sessionId}` | Stream                | 锁座受理       | Redis->RocketMQ 桥接任务、观测任务 | 锁座受理后的异步桥接队列                              |         |

补充：

- `clearSessionCache()` 还会扫描并删除 `seat:{sessionId}:*` 这类兼容遗留 key，但它不是当前在用结构。

---

## 3. 各数据结构详细说明

### 3.1 `seat:state:{sessionId}:{seatId}`

数据结构：

- Redis String
- 值含义：
  - `0` 可选
  - `2` 已售出

写入点：

- 场次初始化时 `initSessionData()` 批量 `SET 0`
- 支付成功时 `confirm_purchase.lua` 把对应座位 `SET 2`

读取点：

- `lock_seat_and_record_order.lua`
- `confirm_purchase.lua`
- `getEffectiveSeatStatuses()`

作用：

- 保存最终售卖状态。
- 锁座时先判断座位是否已经售出。
- 前端展示座位图时作为最终态来源。

生命周期：

- 初始化写入。
- 支付成功后转为 `2`。
- 重新初始化场次或清理缓存时删除。

### 3.2 `seat:lock:{sessionId}:{seatId}`

数据结构：

- Redis String
- 值格式：`userId|lockId`

写入点：

- `lock_seat_and_record_order.lua` 成功后 `SET EX`

读取点：

- 锁座 Lua 校验锁归属或冲突
- `unlock_seat.lua`
- `confirm_purchase.lua`
- `getEffectiveSeatStatuses()`

删除点：

- `unlock_seat.lua` 校验归属后 `DEL`
- `confirm_purchase.lua` 支付确认后 `DEL`
- TTL 自动过期
- 场次清理时按模式删除

作用：

- 表示座位当前被谁临时锁住。
- 与 `seat:state:*` 共同决定座位有效状态。
- 防止同一座位并发重复锁。

TTL：

- 当前锁座主链路里，`seat:lock:*` 的过期时间默认等于“订单过期秒数 + 30 秒缓冲”。

注意：

- 当前“已锁定”不是写入 `seat:state=1`，而是运行时通过 `seat:lock:*` 是否存在推导。

### 3.3 `price:{sessionId}:{seatId}`

数据结构：

- Redis String

写入点：

- `initSessionData()` 初始化时批量 `SET`

读取点：

- `getSeatsPrice()` 批量读取
- `SeckillServiceImpl.lockSeats()` 在锁座前计算 `totalAmount`

作用：

- 把锁座热路径所需价格读取前置到 Redis，避免同步查库算金额。

生命周期：

- 初始化写入。
- 场次清理时删除。

### 3.4 `session:{sessionId}:meta`

数据结构：

- Redis Hash/Map

当前字段：

- `sessionId`
- `eventId`

写入点：

- `saveSessionMeta()` 使用 `RMap.put`

读取点：

- `resolveEventId()` 校验锁座请求里的 `eventId`
- `RedisLockAcceptedBridgeTask.listInitializedSessions()`
- `RedisLockOrderRecoveryTask.listInitializedSessions()`
- `SeckillBacklogSnapshotTask.listInitializedSessions()`

作用：

- 提供 `sessionId -> eventId` 的可信快照。
- 也是桥接、恢复、观测任务发现“哪些场次已初始化”的依据。

生命周期：

- 场次初始化写入。
- 场次清理时删除。

### 3.5 `session:layout:{sessionId}`

数据结构：

- Redis Hash/Map

当前字段：

- `meta`：整体布局元信息 JSON
- `area:{index}`：某个区域的座位列表 JSON

写入点：

- `saveSessionLayout()` 写入 `meta` 和各个 `area:*`

读取点：

- `getSessionLayout()`
- `SeckillServiceImpl.getLayout()`

作用：

- 为前端座位页提供布局快照。
- `getLayout()` 读出静态布局后，再通过 `getEffectiveSeatStatuses()` 刷新实时状态。

生命周期：

- 场次初始化写入。
- 场次清理时删除整个 key。

### 3.6 `order:processing:{orderNo}`

数据结构：

- Redis String/Bucket
- 值类型：`OrderProcessingCacheData` 序列化对象

对象字段：

- `orderNo`
- `userId`
- `sessionId`
- `eventId`
- `seatIds`
- `seatCount`
- `unitPrice`
- `totalAmount`
- `status`
- `paymentStatus`
- `expireTime`
- `createdAt`

写入点：

- 锁座成功后 `saveProcessingCache() -> saveOrderProcessing()`

读取点：

- `OrderServiceImpl.buildProcessingResponseFromCache()`

删除点：

- `markLockOrderOrderCreated()`
- `markLockOrderPaid()`
- `markLockOrderReleased()`

作用：

- 在正式订单还没落到 `orders` 表时，为前端轮询提供“处理中”视图。
- 让锁座接口快速返回后，订单页仍然能立即查到业务上下文。

TTL：

- `max(30秒, 到 expireTime 的剩余秒数 + 30秒)`

### 3.7 `lock:order:{orderNo}`

数据结构：

- Redis Hash/Map

当前字段：

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

写入点：

- `lock_seat_and_record_order.lua` 首次创建
- `update_lock_order_status.lua` 做状态推进

读取点：

- `RedisService.getLockOrder()`
- `SeckillServiceImpl.getLockOrder()`
- `OrderServiceImpl.buildProcessingResponseFromRedisLockOrder()`
- `RedisLockOrderRecoveryTask`
- `RedisLockOrderFlushTask`
- `RedisSeatLockFlushTask`

作用：

- 这是 Redis 侧最核心的业务聚合。
- 它承载“锁座已受理但订单/支付/终态尚在收敛”的全过程。
- 前端轮询在正式订单查不到时，会回退到这里拿到锁单信息。

TTL：

- 首次写入时由 Lua 设置，一般至少 7200 秒。
- 之后每次状态迁移都会续期。

### 3.8 `lock:user:{sessionId}:{userId}`

数据结构：

- Redis String
- 值为 `orderNo`

写入点：

- `lock_seat_and_record_order.lua` 受理成功后 `SET EX`

读取点：

- 同一个 Lua 脚本在锁座前先 `GET`

删除点：

- `update_lock_order_status.lua` 在 `clearUserLockIndex=1` 时删除
- 场次清理时按模式删除
- TTL 自动过期

作用：

- 拦住同一用户同一场次存在未终态锁单时再次锁座。

TTL：

- 当前主链路里，`lock:user:*` 与 `seat:lock:*` 使用同一批过期秒数。

### 3.9 `lock:expire:{sessionId}`

数据结构：

- Redis ZSET

写入点：

- `lock_seat_and_record_order.lua` 使用 `ZADD score=expireTimeMillis member=orderNo`

读取点：

- `RedisLockOrderRecoveryTask` 用 `valueRange` 拉取待检查锁单
- `SeckillBacklogSnapshotTask` 统计 backlog

删除点：

- `update_lock_order_status.lua` 在终态且 `clearUserLockIndex=1` 时 `ZREM`
- `RedisLockOrderRecoveryTask` 在锁单缺失、终态、已建正式订单时移除
- 场次清理时删除整个 ZSET

作用：

- 这是 Redis 侧锁单恢复扫描索引。
- 用于快速找到“应该过期或应该收敛”的锁单。

### 3.10 `stream:lock_accepted:{sessionId}`

数据结构：

- Redis Stream

写入点：

- `lock_seat_and_record_order.lua` 使用 `XADD`

当前消息字段：

- `payloadJson`
- `orderNo`

读取点：

- `RedisLockAcceptedBridgeTask`

额外操作：

- `createGroup()` 创建消费组 `lock-accepted-bridge`
- `readGroup()` 消费新消息
- `getPendingInfo()` 查看 pending
- `autoClaim()` 抢回空闲过久的 pending 消息
- `ack()` 成功桥接后确认

作用：

- 作为 Redis 到 RocketMQ 的桥接缓冲层。
- 锁座成功后，HTTP 线程不直接做正式建单，只把受理结果写进 Stream。

当前生命周期特点：

- 成功桥接后只做 `ACK`，不会逐条 `XDEL` 或 `XTRIM`。
- 当前代码里，这个 Stream 只会在 `clearSessionCache()` 时整体删除。

---

## 4. 按链路阶段看 Redis 操作

## 4.1 场次初始化阶段

入口：

- `SeckillServiceImpl.initSession()`

Redis 操作：

1. `clearSessionCache(sessionId)`
   - 删除 `session:layout:{sessionId}`
   - 删除 `session:{sessionId}:meta`
   - 扫描删除 `seat:state:{sessionId}:*`
   - 扫描删除 `seat:lock:{sessionId}:*`
   - 扫描删除 `price:{sessionId}:*`
   - 扫描删除 `lock:user:{sessionId}:*`
   - 删除 `lock:expire:{sessionId}`
   - 删除 `stream:lock_accepted:{sessionId}`
   - 扫描所有 `lock:order:*`，把其中 `sessionId` 匹配当前场次的锁单删掉
   - 兼容删除遗留 `seat:{sessionId}:*`
2. `initSessionData(sessionId, seatIds, areaPrices)`
   - 批量写 `seat:state:* = 0`
   - 批量写 `price:*`
3. `saveSessionLayout(sessionId, metaJson, areaJsonMap)`
   - 写入 `session:layout:{sessionId}`
4. `saveSessionMeta(sessionId, eventId)`
   - 写入 `session:{sessionId}:meta`

这一阶段的作用：

- 让锁座热路径后续完全依赖 Redis 快照。
- 前端拉取座位图也直接来自 Redis。

## 4.2 前端获取座位图阶段

入口：

- `SeckillServiceImpl.getLayout()`

Redis 操作：

1. 读取 `session:layout:{sessionId}`
2. 从布局 JSON 中抽出全部 `seatId`
3. 批量读取：
   - `seat:state:{sessionId}:{seatId}`
   - `seat:lock:{sessionId}:{seatId}`
4. 根据规则计算实时状态：
   - `seat:state=2` -> 已售出
   - 否则 `seat:lock` 存在 -> 已锁定
   - 否则 -> 可选

这一阶段的作用：

- 静态布局和动态状态分离。
- 不需要频繁重写整张布局图，只刷新实时状态即可。

## 4.3 锁座受理阶段

入口：

- `SeckillServiceImpl.lockSeats()`

Redis 操作前置：

1. `getSessionMeta(sessionId)`
   - 读取 `session:{sessionId}:meta`
   - 校验请求 `eventId` 与快照一致
2. `getSeatsPrice(sessionId, seatIds)`
   - 批量读取 `price:{sessionId}:{seatId}`
   - 计算总金额

核心原子操作：

- `lock_seat_and_record_order.lua`

Lua 中的完整 Redis 操作：

1. `HGET session:{sessionId}:meta eventId`
   - 快照不存在或 `eventId` 不匹配，返回 `4`
2. `GET lock:user:{sessionId}:{userId}`
   - 已存在未终态锁单，返回 `3`
3. 对每个座位执行：
   - `GET seat:state:*`
   - 如果不存在，返回 `1`
   - 如果已售出 `2`，返回 `2`
   - `GET seat:lock:*`
   - 如被别人占用，返回 `2`
   - 如同用户已有占位，返回 `3`
4. 校验通过后，对每个座位：
   - `SET seat:lock:* userId|lockId EX seatLockExpireSeconds`
5. `SET lock:user:{sessionId}:{userId} orderNo EX userLockExpireSeconds`
6. `HSET lock:order:{orderNo} ...`
7. `EXPIRE lock:order:{orderNo} lockOrderTtlSeconds`
8. `ZADD lock:expire:{sessionId} expireTimeMillis orderNo`
9. `XADD stream:lock_accepted:{sessionId} * payloadJson ...`

返回码：

- `0` 成功
- `1` 座位不存在
- `2` 座位不可用
- `3` 用户已有未终态锁单或重复占位
- `4` 场次或 `eventId` 不合法

锁座成功后的补充操作：

- `saveOrderProcessing(order:processing:{orderNo})`

这一阶段的作用：

- 在一次 Lua 原子执行里同时完成“锁座 + 建立锁单聚合 + 建立过期索引 + 建立异步桥接消息”。

## 4.4 Redis Stream 桥接到 RocketMQ 阶段

入口：

- `RedisLockAcceptedBridgeTask`

Redis 操作：

1. 通过扫描 `session:{sessionId}:meta` 找出所有已初始化场次
2. 对每个场次：
   - `createGroup(lock-accepted-bridge)`
   - `readGroup()` 读取新消息
   - `getPendingInfo()` 检查 pending
   - `autoClaim()` 抢回空闲超过 5 秒的 pending
3. 每条消息桥接发送 RocketMQ 成功后：
   - `transitionLockOrderStatus()`
   - 其底层 Lua 会：
     - `HGET lock:order:{orderNo} status`
     - 比对期望状态
     - `HSET status=ORDER_CREATING`
     - `HSET paymentStatus=NOT_READY`
     - `HSET updatedAtMillis`
     - `EXPIRE lock:order:{orderNo}`
   - `ACK stream`

这一阶段的作用：

- 把 Redis 接受锁座与 MQ 建单异步解耦。
- pending + autoClaim 机制避免桥接线程异常后消息永久卡死。

## 4.5 正式订单创建进行中与前端轮询阶段

前端轮询入口：

- `OrderServiceImpl.getOrderByNo()`

Redis 读取顺序中的相关部分：

1. 先查正式 `orders`
2. 正式订单不存在时，查 `order:processing:{orderNo}`
3. 再查 `lock:order:{orderNo}`
4. 最后走 `seckill-service` 内部兜底接口，而该接口本质上还是优先查 Redis 锁单

Redis 在这一阶段的作用：

- 正式订单还没完成之前，前端仍能拿到：
  - `orderNo`
  - `seatIds`
  - `expireTime`
  - `status=PROCESSING`
  - `paymentStatus`
  - `nextPollMs`

状态推进：

- `OrderCreatedInternalConsumer -> SeckillServiceImpl.markLockOrderOrderCreated()`

对应 Redis 操作：

1. 读取 `lock:order:{orderNo}`
2. 若当前状态是 `LOCKED/ORDER_CREATING`
3. 调用 `transitionLockOrderStatus()` 更新为 `ORDER_CREATED`
4. 删除 `order:processing:{orderNo}`

这一阶段的作用：

- 让正式订单创建和 Redis 锁单聚合状态收敛。
- 当前端下一次查询时，就可以逐步进入支付准备阶段。

## 4.6 支付准备阶段

入口：

- `OrderServiceImpl.enrichPaymentInfo()`

Redis 直接操作：

- 无新增写入。

Redis 间接作用：

- 前端能否进入支付准备阶段，依赖前一阶段 Redis 锁单与 processing 缓存是否正确支撑轮询。

## 4.7 支付成功阶段

入口：

- `OrderPaidConsumer` in `seckill-service`

Redis 操作：

1. `confirmPurchase(sessionId, userId, lockId, seatIds)`
2. 对应 `confirm_purchase.lua` 的逻辑：
   - 逐个 `GET seat:state:*`
   - 若状态不是已售出，则继续 `GET seat:lock:*`
   - 只有锁归属与 `userId + lockId` 完全匹配才允许继续
   - 最终对每个座位：
     - `SET seat:state:* = 2`
     - `DEL seat:lock:*`
3. `markLockOrderPaid(orderNo)`
   - 调用 `transitionLockOrderStatus()`
   - `status -> PAID`
   - `paymentStatus -> SUCCESS`
   - 删除 `failReason`
   - `EXPIRE lock:order:{orderNo}`
   - `DEL lock:user:{sessionId}:{userId}`
   - `ZREM lock:expire:{sessionId} orderNo`
4. 删除 `order:processing:{orderNo}`

这一阶段的作用：

- 把 Redis 中的临时锁态收敛为最终售出态。
- 清理该用户未终态锁单索引和过期索引。

## 4.8 用户取消或超时取消阶段

入口：

- `OrderCancelConsumer`
- 或恢复任务命中过期锁单

Redis 操作：

1. `unlockSeats(sessionId, userId, lockId, seatIds)`
2. 对应 `unlock_seat.lua`：
   - 逐个 `GET seat:lock:*`
   - 若 key 已不存在，也视作可释放计数 +1
   - 若存在，则只有归属完全匹配才 `DEL`
3. `markLockOrderReleased(orderNo, targetStatus, failReason)`
   - `transitionLockOrderStatus()`
   - `status -> TIMEOUT` 或 `CANCELLED`
   - `paymentStatus -> NOT_AVAILABLE`
   - `HSET failReason`
   - `DEL lock:user:{sessionId}:{userId}`
   - `ZREM lock:expire:{sessionId} orderNo`
4. 删除 `order:processing:{orderNo}`

这一阶段的作用：

- 释放临时锁，但不会把 `seat:state` 设成已售。
- Redis 里的锁单状态进入终态，前端停止继续轮询支付。

## 4.9 Redis 锁单恢复阶段

入口：

- `RedisLockOrderRecoveryTask`

Redis 操作：

1. 扫描所有已初始化场次
2. 读取 `lock:expire:{sessionId}` 的前一批 `orderNo`
3. 对每个 `orderNo`：
   - 读取 `lock:order:{orderNo}`
   - 如果锁单不存在：`ZREM`
   - 如果锁单已终态或已 `ORDER_CREATED`：`ZREM`
   - 如果状态仍是 `LOCKED/ORDER_CREATING` 且已陈旧：
     - 若正式订单已存在：推进锁单到 `ORDER_CREATED` 并 `ZREM`
     - 若已过期且仍未建单：释放座位，推进到 `TIMEOUT`

这一阶段的作用：

- 收拾“锁座已受理，但桥接/建单/状态推进某一段没正常收敛”的尾巴。

## 4.10 MySQL 异步刷盘阶段

入口：

- `RedisLockOrderFlushTask`
- `RedisSeatLockFlushTask`

Redis 操作：

1. 扫描 `lock:order:*`
2. 对每个 key 读取 `lock:order:{orderNo}`
3. 将聚合内容刷入：
   - `lock_orders`
   - `seat_locks`

这一阶段的作用：

- Redis 仍是热路径状态源。
- MySQL 中的 `lock_orders`、`seat_locks` 更像审计与追踪落盘。

## 4.11 观测与压测分析阶段

入口：

- `SeckillBacklogSnapshotTask`

Redis 操作：

1. 扫描 `session:{sessionId}:meta`
2. 统计每个场次：
   - `lock:expire:{sessionId}.size()`
   - `stream:lock_accepted:{sessionId}.size()`
   - `stream pending count`

这一阶段的作用：

- 在压测时观察 Redis 侧是否出现：
  - 过期队列堆积
  - Stream 堆积
  - pending 累积

---

## 5. Lua 脚本逐个说明

### 5.1 `lock_seat_and_record_order.lua`

用途：

- 当前锁座主脚本。

它把以下动作合并到一次原子执行中：

1. 校验场次快照 `session:{sessionId}:meta`
2. 校验用户锁单索引 `lock:user:*`
3. 校验座位最终状态 `seat:state:*`
4. 校验座位临时锁 `seat:lock:*`
5. 写入座位临时锁 `seat:lock:*`
6. 写入用户锁单索引 `lock:user:*`
7. 写入锁单聚合 `lock:order:*`
8. 写入过期索引 `lock:expire:*`
9. 写入桥接 Stream `stream:lock_accepted:*`

这是当前 Redis 热路径的核心。

### 5.2 `unlock_seat.lua`

用途：

- 释放临时锁。

操作：

1. 逐个读取 `seat:lock:*`
2. 若 key 不存在，直接计入已释放数量
3. 若存在且归属匹配，则 `DEL`

特点：

- 它不会回写 `seat:state:*`
- 也就是说取消/超时只是释放锁，不会把座位标成已售

### 5.3 `confirm_purchase.lua`

用途：

- 支付成功后的 Redis 最终确认。

操作：

1. 逐个读取 `seat:state:*`
2. 如果还不是已售出，则检查 `seat:lock:*` 是否归属于当前 `userId|lockId`
3. 校验通过后：
   - `SET seat:state:* = 2`
   - `DEL seat:lock:*`

特点：

- 这是 Redis 中从“临时锁”切换到“最终售出”的关键脚本。

### 5.4 `update_lock_order_status.lua`

用途：

- Redis 锁单聚合状态迁移脚本。

操作：

1. 检查 `lock:order:{orderNo}` 是否存在
2. `HGET status`
3. 只有当前状态命中 `expectedStatuses` 才允许迁移
4. `HSET status`
5. 可选 `HSET paymentStatus`
6. 可选 `HSET failReason` 或 `HDEL failReason`
7. `HSET updatedAtMillis`
8. `EXPIRE lock:order:{orderNo}`
9. 如果 `clearUserLockIndex=1`：
   - 读取 `sessionId`、`userId`
   - `DEL lock:user:{sessionId}:{userId}`
   - `ZREM lock:expire:{sessionId} orderNo`

作用：

- 统一承担 `ORDER_CREATING`、`ORDER_CREATED`、`PAID`、`TIMEOUT`、`CANCELLED` 等状态推进。

---

## 6. Redis 与链路职责对照

| 链路阶段 | 主要 Redis key | 主要职责 |
| --- | --- | --- |
| 场次初始化 | `seat:state:*` `price:*` `session:*:meta` `session:layout:*` | 建立场次快照和热路径静态数据 |
| 拉取座位图 | `session:layout:*` `seat:state:*` `seat:lock:*` | 组合静态布局和实时状态 |
| 锁座受理 | `session:*:meta` `price:*` `seat:state:*` `seat:lock:*` `lock:user:*` `lock:order:*` `lock:expire:*` `stream:lock_accepted:*` `order:processing:*` | 原子锁座、建立锁单聚合、建立桥接与恢复索引 |
| 桥接 MQ | `stream:lock_accepted:*` `lock:order:*` | 异步建单前的桥接和状态推进 |
| 订单轮询 | `order:processing:*` `lock:order:*` | 正式订单未就绪时的查询兜底 |
| 支付成功 | `seat:state:*` `seat:lock:*` `lock:order:*` `lock:user:*` `lock:expire:*` | 把临时锁收敛为最终售出态 |
| 取消 / 超时 | `seat:lock:*` `lock:order:*` `lock:user:*` `lock:expire:*` | 释放临时锁并推进锁单终态 |
| 恢复 | `lock:expire:*` `lock:order:*` | 处理未收敛锁单 |
| 观测 | `lock:expire:*` `stream:lock_accepted:*` `session:*:meta` | 观察 backlog 和 pending |

---

## 7. 当前未走热链路但仍存在于代码中的 Redis 点

1. `seat:{sessionId}:*`
   - 不是当前结构，只在 `clearSessionCache()` 中兼容清理。

---

## 8. 一句话总结

当前 Redis 设计的核心不是“缓存数据库结果”，而是：

- 用 `seat:state:* + seat:lock:*` 承担热路径座位状态源
- 用 `lock:order:*` 承担锁单聚合状态源
- 用 `lock:expire:* + stream:lock_accepted:*` 承担异步收敛索引
- 用 `order:processing:*` 承担前端轮询短期视图

这样锁座接口才能把正式建单、支付准备、最终收敛都从同步请求里剥离出去。
