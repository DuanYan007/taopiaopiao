# 秒杀链路手动测试观测文档

## 1. 适用范围

这份文档用于本地手动验证以下链路：

- 锁座受理
- Redis Stream -> RocketMQ 桥接
- 正式订单创建
- 支付成功
- 超时取消
- 用户主动取消

目标不是只看接口返回，而是在每个阶段同时观察：

- Redis 是否进入预期状态
- MySQL 是否已经收敛
- `seckill-service` / `order-service` 日志是否出现关键事件

## 2. 先记住的异步窗口

当前链路不是强一致，以下延迟是正常现象：

- `RedisLockAcceptedBridgeTask`：`1s` 一轮
- `RedisLockOrderRecoveryTask`：`2s` 一轮
- `RedisLockOrderFlushTask`：`5s` 一轮
- `RedisSeatLockFlushTask`：首次 `7s`，之后 `5s` 一轮
- `SeckillBacklogSnapshotTask`：`10s` 一轮

所以要分两层看结果：

- `0-1s`：主要看 Redis 快路径
- `5-10s`：再看 MySQL 刷盘和 backlog 日志是否收敛

## 3. 观测变量

先从锁座响应里记录这些值：

- `SESSION_ID`
- `USER_ID`
- `ORDER_NO`
- `LOCK_ID`
- `SEAT_ID`

如果一次锁多个座位，把 `SEAT_ID` 扩展成多个值。

## 4. 常用观测命令

### 4.1 Redis

```bash
redis-cli -h 127.0.0.1 -p 6349 -a 7566
```

```redis
HGETALL session:{SESSION_ID}:meta
GET order:processing:{ORDER_NO}
HGETALL lock:order:{ORDER_NO}
GET lock:user:{SESSION_ID}:{USER_ID}
ZSCORE lock:expire:{SESSION_ID} {ORDER_NO}
ZRANGE lock:expire:{SESSION_ID} 0 -1 WITHSCORES
GET seat:state:{SESSION_ID}:{SEAT_ID}
GET seat:lock:{SESSION_ID}:{SEAT_ID}
TTL seat:lock:{SESSION_ID}:{SEAT_ID}
XLEN stream:lock_accepted:{SESSION_ID}
XPENDING stream:lock_accepted:{SESSION_ID} lock-accepted-bridge
XRANGE stream:lock_accepted:{SESSION_ID} - + COUNT 5
```

说明：

- `stream:lock_accepted:*` 被消费并 `ACK` 后，`XLEN` 不一定变小，因为历史消息还在；真正要看的是 `XPENDING` 是否回到 `0`。
- `lock:expire:{sessionId}` 里的 `orderNo` 不一定在建单成功瞬间立刻消失，恢复任务下一轮会清理。

### 4.2 MySQL

```bash
mysql -h127.0.0.1 -P3306 -uroot -p7566 taopiaopiao
```

```sql
SELECT order_no, user_id, session_id, lock_id, status, pay_time, expire_time, cancel_time, created_at, updated_at
FROM orders
WHERE order_no = '{ORDER_NO}';

SELECT order_no, lock_id, user_id, session_id, status, fail_reason, expire_time, created_at, updated_at
FROM lock_orders
WHERE order_no = '{ORDER_NO}';

SELECT order_no, seat_id, status, user_id, session_id, lock_id, expire_time, updated_at
FROM seat_locks
WHERE order_no = '{ORDER_NO}'
ORDER BY seat_id;
```

状态码：

- `orders.status`
  - `0` 处理中视图，不落表
  - `1` 未支付
  - `2` 已支付
  - `3` 已取消
  - `4` 已退款
  - `5` 超时取消
- `lock_orders.status`
  - `1` 已锁定
  - `2` 订单创建中
  - `3` 订单已创建
  - `4` 已支付
  - `5` 超时取消
  - `6` 已取消
  - `7` 失败
- `seat_locks.status`
  - `0` 已释放
  - `1` 已锁定
  - `2` 已支付
  - `3` 已超时释放

### 4.3 日志关键字

重点盯这几类日志：

- `seckill-service`
  - `RocketMQ Topic 已就绪`
  - `RocketMQ Topic 创建完成`
  - `发送锁座受理消息成功`
  - `桥接 Redis Stream -> RocketMQ 失败`
  - `锁单已更新为正式订单已创建`
  - `收到支付成功消息`
  - `支付成功副作用处理完成`
  - `收到订单取消消息`
  - `处理订单取消消息成功`
  - `Redis 恢复命中已过期未建单锁单，已释放`
  - `seckill-backlog-snapshot`
- `order-service`
  - `收到锁座受理消息`
  - `异步建单事务消息已发送`
  - `执行本地事务`
  - `本地事务成功，订单已创建`
  - `发送内部订单创建完成消息`
  - `延迟超时检查消息已发送`
  - `收到支付成功事件`
  - `订单支付成功，状态已更新`
  - `超时检查命中已支付，已补发支付成功消息`
  - `超时检查确认未支付，已发送超时取消消息`

## 5. 阶段 0：服务启动后基线检查

### 预期 Redis

- `HGETALL session:{SESSION_ID}:meta` 能查到 `sessionId`、`eventId`
- 目标座位的 `seat:state:{SESSION_ID}:{SEAT_ID}` 应该是 `0`
- `seat:lock:{SESSION_ID}:{SEAT_ID}` 应该不存在

### 预期 MySQL

- 测试前的 `orders`、`lock_orders`、`seat_locks` 不应存在上一轮脏数据

### 预期日志

- `seckill-service` / `order-service` 启动后应出现：
  - `RocketMQ Topic 已就绪`
  - 或 `RocketMQ Topic 创建完成`

如果启动后立刻出现以下日志，先不要继续测：

- `RocketMQ Topic 初始化失败`
- `No route info of this topic`

## 6. 阶段 1：锁座接口刚返回成功

以 `/seckill/lock` 返回成功的瞬间为准。

### 接口响应应该看到

- `lockId`
- `orderNo`
- `expireTime`
- `orderStatus=PROCESSING`
- `paymentStatus=NOT_READY`

### 预期 Redis

- `GET order:processing:{ORDER_NO}` 存在
- `HGETALL lock:order:{ORDER_NO}` 存在，关键字段应接近：
  - `status=1`
  - `paymentStatus=NOT_READY`
  - `lockId={LOCK_ID}`
  - `userId={USER_ID}`
  - `sessionId={SESSION_ID}`
- `GET lock:user:{SESSION_ID}:{USER_ID}` 存在
- `ZSCORE lock:expire:{SESSION_ID} {ORDER_NO}` 有值
- `GET seat:lock:{SESSION_ID}:{SEAT_ID}` 存在，值形如 `{USER_ID}|{LOCK_ID}`
- `GET seat:state:{SESSION_ID}:{SEAT_ID}` 仍然是 `0`
- `XLEN stream:lock_accepted:{SESSION_ID}` 增加

### 预期 MySQL

这个时刻允许还没有数据，尤其是：

- `orders` 可能还没有
- `lock_orders` 可能还没有
- `seat_locks` 可能还没有

### 异常信号

- `order:processing:{ORDER_NO}` 不存在，同时 `lock:order:{ORDER_NO}` 也不存在
- `seat:lock:{SESSION_ID}:{SEAT_ID}` 没写进去
- 锁座刚成功但日志立刻出现 `写 processing 缓存失败，不影响锁座受理`

## 7. 阶段 2：桥接任务已把消息送到 RocketMQ

通常在 `1s` 内出现。

### 预期 Redis

- `HGETALL lock:order:{ORDER_NO}` 中：
  - `status=2`
  - `paymentStatus=NOT_READY`
- `XPENDING stream:lock_accepted:{SESSION_ID} lock-accepted-bridge` 应该回到 `0`
- `XRANGE stream:lock_accepted:{SESSION_ID} - + COUNT 5` 还能看到历史消息，这正常

### 预期 MySQL

- `orders` 可能刚出现，也可能还没出现
- `lock_orders` / `seat_locks` 仍允许晚一点刷盘

### 预期日志

- `seckill-service`
  - `发送锁座受理消息成功: orderNo=..., lockId=...`
- `order-service`
  - `收到锁座受理消息: orderNo=..., lockId=..., userId=...`
  - `异步建单事务消息已发送: orderNo=...`

### 异常信号

- `lock:order.status` 长时间停在 `1`
- `XPENDING` 一直大于 `0`
- `seckill-service` 出现 `桥接 Redis Stream -> RocketMQ 失败`

## 8. 阶段 3：正式订单已创建

通常在桥接成功后很快出现，MySQL 刷盘观测建议等 `5-10s` 再看。

### 预期 Redis

- `GET order:processing:{ORDER_NO}` 应该已经被删除
- `HGETALL lock:order:{ORDER_NO}` 中：
  - `status=3`
  - `paymentStatus=NOT_READY`
- `GET lock:user:{SESSION_ID}:{USER_ID}` 仍存在
- `GET seat:lock:{SESSION_ID}:{SEAT_ID}` 仍存在
- `GET seat:state:{SESSION_ID}:{SEAT_ID}` 仍是 `0`

说明：

- 正式订单创建完成后，`paymentStatus` 在 Redis 锁单里仍可能是 `NOT_READY`，这正常。
- 前端轮询看到的 `READY` 是 `order-service` 结合支付系统结果实时补齐出来的，不会回写到 `lock:order`.

### 预期 MySQL

- `orders`
  - 应存在一行
  - `status=1`
  - `pay_time IS NULL`
  - `cancel_time IS NULL`
- `lock_orders`
  - 在刷盘任务执行后应存在一行
  - `status=3`
  - `fail_reason IS NULL`
- `seat_locks`
  - 在刷盘任务执行后应存在多行
  - `status=1`

### 预期日志

- `order-service`
  - `执行本地事务: orderNo=...`
  - `本地事务成功，订单已创建: orderNo=..., userId=..., amount=...`
  - `发送内部订单创建完成消息: orderNo=..., lockId=...`
  - `延迟超时检查消息已发送: orderNo=...`
- `seckill-service`
  - `锁单已更新为正式订单已创建: orderNo=..., lockId=...`

### 异常信号

- `orders` 已落表，但 `lock:order.status` 长时间不是 `3`
- `orders` 已落表，但 `order:processing:{ORDER_NO}` 仍长期存在
- `seckill-service` 出现 `处理内部订单创建完成消息异常`

## 9. 阶段 4：支付前轮询

这是用户还没支付、订单页不断查询 `/client/orders/{orderNo}` 的阶段。

### 预期 Redis

- `order:processing:{ORDER_NO}` 一般已不存在
- `lock:order:{ORDER_NO}` 仍是：
  - `status=3`
  - `paymentStatus=NOT_READY`

### 预期 MySQL

- `orders.status=1`
- `lock_orders.status=3`
- `seat_locks.status=1`

### 预期日志

- 不一定有固定成功日志
- 如果支付系统临时不可用，可能看到：
  - `补齐支付信息失败: orderNo=...`

### 额外说明

- 订单查询接口在这个阶段可能返回：
  - `status=1`
  - `paymentStatus=READY`
  - `payUrl` 非空
- 这是正常现象，即使 Redis 锁单里的 `paymentStatus` 还是 `NOT_READY`。

## 10. 阶段 5：支付成功

支付回调或模拟支付成功后，重点观察终态收敛。

### 预期 Redis

- `GET order:processing:{ORDER_NO}` 不存在
- `HGETALL lock:order:{ORDER_NO}` 中：
  - `status=4`
  - `paymentStatus=SUCCESS`
- `GET lock:user:{SESSION_ID}:{USER_ID}` 应该已删除
- `GET seat:lock:{SESSION_ID}:{SEAT_ID}` 应该已删除
- `GET seat:state:{SESSION_ID}:{SEAT_ID}` 应该变成 `2`
- `ZSCORE lock:expire:{SESSION_ID} {ORDER_NO}` 后续会被恢复任务清掉

### 预期 MySQL

- `orders`
  - `status=2`
  - `pay_time IS NOT NULL`
- `lock_orders`
  - `status=4`
- `seat_locks`
  - 对应座位都应为 `status=2`

### 预期日志

- `order-service`
  - `收到支付成功事件: orderNo=..., userId=..., sessionId=...`
  - `订单支付成功，状态已更新: orderNo=...`
- `seckill-service`
  - `收到支付成功消息: orderNo=..., userId=..., sessionId=...`
  - `支付成功副作用处理完成: orderNo=..., seatCount=...`

### 异常信号

- `orders.status=2`，但 `seat:state` 还是 `0`
- `orders.status=2`，但 `seat:lock` 还在
- `seat_locks` 长时间停在 `1`
- `seckill-service` 出现 `Redis 确认购买失败`
- `seckill-service` 出现 `处理支付成功消息异常`

## 11. 阶段 6：超时取消

不支付，等超时检查消息触发。

### 预期 Redis

- `HGETALL lock:order:{ORDER_NO}` 中：
  - `status=5`
  - `paymentStatus=NOT_AVAILABLE`
  - `failReason` 可能为 `TIMEOUT` 或恢复链路写入的 `REDIS_RECOVERY_TIMEOUT`
- `GET lock:user:{SESSION_ID}:{USER_ID}` 应已删除
- `GET seat:lock:{SESSION_ID}:{SEAT_ID}` 应已删除
- `GET seat:state:{SESSION_ID}:{SEAT_ID}` 应保持 `0`

### 预期 MySQL

- `orders`
  - `status=5`
  - `cancel_time IS NOT NULL`
- `lock_orders`
  - `status=5`
  - `fail_reason` 可能为空，也可能带超时原因
- `seat_locks`
  - `status=3`

### 预期日志

- `order-service`
  - `超时检查确认未支付，已发送超时取消消息: orderNo=...`
- `seckill-service`
  - `收到订单取消消息: orderNo=..., reason=TIMEOUT, seatIds=...`
  - `处理订单取消消息成功: orderNo=..., reason=TIMEOUT`

### 异常信号

- `orders.status=5`，但 `seat:lock` 还在
- `orders.status=5`，但 `seat_locks.status` 还是 `1`
- `order-service` 出现 `处理超时检查消息异常`
- `seckill-service` 出现 `处理订单取消消息异常`

## 12. 阶段 7：用户主动取消

用户在未支付状态下调用取消接口。

### 预期 Redis

- `lock:order:{ORDER_NO}`
  - `status=6`
  - `paymentStatus=NOT_AVAILABLE`
- `lock:user:{SESSION_ID}:{USER_ID}` 删除
- `seat:lock:{SESSION_ID}:{SEAT_ID}` 删除
- `seat:state:{SESSION_ID}:{SEAT_ID}` 仍是 `0`

### 预期 MySQL

- `orders.status=3`
- `orders.cancel_time IS NOT NULL`
- `lock_orders.status=6`
- `seat_locks.status=0`

### 预期日志

- `order-service`
  - `发送订单取消消息: orderNo=..., reason=USER`
  - `订单取消成功: orderNo=..., userId=...`
- `seckill-service`
  - `收到订单取消消息: orderNo=..., reason=USER, seatIds=...`
  - `处理订单取消消息成功: orderNo=..., reason=USER`

## 13. backlog 日志怎么看

每 `10s` 会有一条：

```text
seckill-backlog-snapshot ...
```

重点字段：

- `lockOrdersLockedCount`
- `lockOrdersOrderCreatingCount`
- `lockOrdersOrderCreatedCount`
- `redisExpireQueueCount`
- `redisLockAcceptedStreamSize`
- `redisLockAcceptedPendingCount`
- `bridgeSentTotal`
- `bridgeFailedTotal`
- `recoveryTimeoutTotal`
- `recoveryOrderCreatedCatchupTotal`

经验判断：

- 正常手测后，`redisLockAcceptedPendingCount` 应尽量回到 `0`
- 正常手测后，`lockOrdersOrderCreatingCount` 不应长期堆积
- 如果 `bridgeFailedTotal` 持续增长，先查 MQ 路由或 broker
- 如果 `recoveryOrderCreatedCatchupTotal` 增长，说明桥接或内部消息阶段出现过短暂丢失，但恢复任务兜住了

## 14. 常见异常与定位入口

### 14.1 锁座成功，但一直没有正式订单

先看：

- Redis：`lock:order.status` 是否长期卡在 `1/2`
- 日志：`桥接 Redis Stream -> RocketMQ 失败`
- 日志：`收到锁座受理消息` 是否缺失

### 14.2 正式订单已创建，但订单页还一直是 PROCESSING

先看：

- `order:processing:{ORDER_NO}` 是否没删掉
- `lock:order.status` 是否没到 `3`
- `发送内部订单创建完成消息` 是否出现
- `锁单已更新为正式订单已创建` 是否出现

### 14.3 订单已支付，但座位没售出

先看：

- `orders.status` 是否为 `2`
- Redis `seat:state` 是否仍为 `0`
- Redis `seat:lock` 是否还存在
- `处理支付成功消息异常`
- `Redis 确认购买失败`

### 14.4 超时后座位没释放

先看：

- `orders.status` 是否为 `5`
- Redis `seat:lock` 是否还在
- `seat_locks.status` 是否仍为 `1`
- `处理超时检查消息异常`
- `处理订单取消消息异常`

## 15. 推荐手测顺序

建议按这个顺序做一轮完整验证：

1. 重启 `seckill-service` 和 `order-service`
2. 确认启动日志里 `RocketMQ Topic 已就绪` 或 `RocketMQ Topic 创建完成`
3. 发起一次锁座，请求成功后立刻记录 `ORDER_NO`、`LOCK_ID`
4. 先看 Redis 快路径，确认阶段 1
5. 等 `1-2s`，确认阶段 2
6. 等 `5-10s`，确认阶段 3 的 MySQL 刷盘
7. 做一轮订单查询，确认阶段 4
8. 走支付成功，确认阶段 5
9. 再做一笔不支付订单，确认阶段 6
10. 再做一笔主动取消订单，确认阶段 7

这样能一次把受理、建单、支付、超时、取消五类状态全覆盖。
  docker run --rm --network host \
    -e SESSION_ID=2 \
    -e USERS=1600 \
    -e USER_BASE=100000 \
    -e SEAT_START_ID=161 \
    -e SEAT_END_ID=1760 \
    -e SEATS_PER_REQUEST=1 \
    -e MAX_ATTEMPTS_PER_USER=30 \
    -e REQUEST_INTERVAL_SECONDS=1 \
    -e MAX_DURATION=40s \
    -v "$(pwd)/scripts/loadtest:/scripts" \
    grafana/k6 run /scripts/lock_only_burst.js

