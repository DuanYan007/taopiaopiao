# 项目中的重试与幂等设计

这份文档只描述当前代码中的真实实现，目标是回答 5 个问题：

- 项目里哪些地方会自动重试，哪些地方不会
- 每条链路的幂等键是什么
- 前端、OpenResty、后端各自承担什么防重职责
- MQ 重复投递、事务回查、恢复任务为什么不会把状态打乱
- 当前哪些字段只是链路追踪，不是业务幂等键

结论先说：

- 当前项目不是靠单一“全局幂等框架”保证安全，而是靠多层次组合：
  - 前端轮询与按钮禁用
  - OpenResty 短时防重复
  - Redis Lua 原子校验
  - `orderNo` / `lockId` 作为核心业务键
  - SQL 条件更新
  - RocketMQ 消费者重复消费容忍
  - Redis Stream pending / auto-claim
  - 定时恢复任务最终收敛
- `requestId` 主要用于链路追踪和排查，不是当前主业务幂等键。

---

## 1. 核心原则

当前项目的重试与幂等设计遵循 4 个原则：

1. 高频热点入口先在 OpenResty 和 Redis 层截住，不能一上来就压数据库。
2. 异步链路默认接受“至少一次投递”，消费者必须自己做幂等。
3. 正式状态推进尽量使用“条件更新”或“期望状态匹配”，避免重复消息把状态回滚。
4. 超时、取消、支付成功三条链路不能互相踩踏，最终要能自动收敛。

---

## 2. 项目里的幂等键清单

| 幂等键 / 防重键 | 所在层 | 作用 |
| --- | --- | --- |
| `sessionId + userId + seatFingerprint` | OpenResty | 短时重复点击防重、终态短路、最近不可用短路 |
| `sessionId + seatId` | Redis | 单座位热点状态键 |
| `sessionId + userId` | Redis | 用户在同一场次只能持有一个未终态锁单 |
| `userId + lockId` | Redis Lua / seat lock | 确认锁归属，确保只有锁持有者能释放或确认购买 |
| `orderNo` | Redis / MySQL / MQ / 支付 | 当前项目最核心的业务幂等键 |
| `lockId` | Redis / MySQL | 锁单、座位锁审计链路的归属键 |
| `order_no UNIQUE` | MySQL `orders` | 防止正式订单重复插入 |
| `lock_orders.order_no UNIQUE` | MySQL `lock_orders` | 防止锁单审计重复插入 |
| `lock_orders.lock_id UNIQUE` | MySQL `lock_orders` | 防止锁单审计重复插入 |
| `payment orderNo` | payment-system | 幂等创建支付记录 |

说明：

- `orderNo` 是当前跨 Redis、MySQL、MQ、支付系统的统一业务键。
- `lockId` 更偏向锁归属和审计。
- `requestId` 当前只会进入日志、Redis 锁单聚合和 `lock_orders`，用于追踪，不参与主链路去重判定。

---

## 3. 前端的重试与防重

## 3.1 公共请求层没有统一自动重试

前端公共请求封装在：

- [/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-api.js](/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-api.js)

当前行为：

- `clientGet`
- `clientPost`
- `clientPut`
- `clientDelete`

都只是一次 `fetch()` 调用，没有内建自动重试、没有请求幂等层、也没有自动补 `X-Request-Id`。

这意味着：

- 锁座失败时，前端默认不会自动重试锁座请求。
- 取消订单、删除订单、普通查询也没有统一自动重试。

## 3.2 选座页对锁座没有自动重试

锁座入口在：

- [/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-seat-selection.js](/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-seat-selection.js)

当前行为：

1. 用户点击锁座
2. 前端调用 `/api/seckill/lock`
3. 如果返回成功，立即保存 `lockId`、`orderNo`、`nextPollMs` 等信息到 `sessionStorage`
4. 跳转到支付确认页
5. 如果失败，只提示错误并刷新座位状态，不自动重发同一请求

这意味着：

- 锁座请求的重复提交，主要不是前端自动重试造成的，而是用户重复点击、网络抖动后手动重试造成的。
- 这也是为什么 OpenResty 要额外做短时防重。

## 3.3 支付确认页会自动轮询订单详情

支付确认页逻辑在：

- [/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-order-confirm.js](/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-order-confirm.js)

这里是当前前端最明确的“自动重试”实现。

### 3.3.1 轮询目标

前端不断请求：

- `GET /api/client/orders/{orderNo}`

目的不是重试锁座，而是等待异步链路完成：

- 正式订单创建
- 支付记录补齐
- 支付链接就绪

### 3.3.2 轮询重试策略

当前实现：

1. 页面初始化后执行 `waitForPaymentReady()`
2. 每次请求成功后读取后端返回的 `nextPollMs`
3. 如果请求失败，则使用前端兜底退避逻辑 `computeFallbackPollMs()`
4. 下次等待时间会在 `800ms` 到 `5000ms` 之间收敛

当前前端轮询具有这些特点：

- 有自动重试
- 有退避
- 有截止时间
- 不会无限轮询

### 3.3.3 轮询停止条件

前端会在以下情况下停止继续轮询：

1. `paymentStatus = READY`
   - 支付信息已就绪，停止轮询，启用支付按钮
2. `paymentStatus = SUCCESS`
   - 已支付，停止轮询
3. `paymentStatus = NOT_AVAILABLE`
   - 订单已经终态，不再可支付，停止轮询
4. 达到轮询截止时间
   - 截止时间取 `min(订单过期时间, 当前时间 + 120秒)`

所以前端的“自动重试”本质上是：

- 只重试订单查询
- 不自动重试锁座
- 不自动重试取消订单

## 3.4 支付按钮有页面级防重

支付按钮逻辑同样在：

- [/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-order-confirm.js](/home/duanyan/project/taopiaopiao-frontend/html/assets/js/client-order-confirm.js)

当前行为：

1. 点击支付后立即将按钮置为 disabled
2. 按钮文案变成“跳转中...”
3. 如果 `payUrl` 缺失则抛错并恢复按钮
4. 否则新开窗口跳转支付页，同时当前页跳去订单中心

这层防重不是业务级幂等，只是页面级防抖，作用是：

- 避免用户在一次页面渲染中连续点击多次支付按钮

---

## 4. OpenResty 的防重与短路

OpenResty 闸门在：

- [/usr/local/openresty/nginx/conf/app.conf](/usr/local/openresty/nginx/conf/app.conf)
- [/usr/local/openresty/nginx/lua/seckill_gate.lua](/usr/local/openresty/nginx/lua/seckill_gate.lua)

这一层的作用不是业务最终幂等，而是入口防抖和短时记忆。

## 4.1 `X-Request-Id` 自动补齐

实现文件：

- [/usr/local/openresty/nginx/lua/seckill_gate_util.lua](/usr/local/openresty/nginx/lua/seckill_gate_util.lua)

当前行为：

- 如果请求头里没有 `X-Request-Id`，OpenResty 会自动生成并补上

作用：

- 便于全链路日志排查
- 便于把一次锁座请求串起来

注意：

- 这不是业务幂等键。
- 当前后端不会因为 `X-Request-Id` 相同就拒绝第二次业务执行。

## 4.2 基于座位指纹的三层短时防重

OpenResty 会构造：

- `seatFingerprint = sessionId + 排序后的 seatIds`

然后围绕：

- `sessionId + userId + seatFingerprint`

做 3 类短路。

### 4.2.1 terminal hold

命中条件：

- 上一次同用户、同场次、同座位组合已经成功锁座
- 或后端返回了“重复购票”业务码

当前效果：

- 后续重复请求直接在 OpenResty 返回 `409`

### 4.2.2 recent unavailable hold

命中条件：

- 后端刚刚返回业务码 `2`，即“座位已被锁定或售出”

当前效果：

- 在短时间内，相同座位组合会被直接拦截，不再继续打后端

### 4.2.3 dedupe cooldown

命中条件：

- 同用户在极短时间内重复提交完全相同的选座请求

当前效果：

- 直接返回 `409`

## 4.3 OpenResty 的本质

这一层需要明确：

- 它做的是“前置防重复提交”
- 不是业务最终一致性的幂等保证

因为：

- 它只存在于单机 `lua_shared_dict`
- 它的键是短 TTL
- 它不会作为正式业务状态源

真正的业务幂等仍然在后端 Redis、MySQL、MQ 消费者那一层。

---

## 5. 秒杀服务的重试与幂等

## 5.1 锁座受理依赖 Redis Lua 原子校验

核心入口：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/service/impl/SeckillServiceImpl.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/service/impl/SeckillServiceImpl.java)
- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/lock_seat_and_record_order.lua](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/lock_seat_and_record_order.lua)

锁座幂等 / 防冲突点：

1. 校验 `session -> eventId` 快照一致
2. 校验 `lock:user:{sessionId}:{userId}` 不存在
3. 校验每个 `seat:state:*` 不为已售
4. 校验每个 `seat:lock:*` 不被别人占有
5. 通过后一次 Lua 原子写入：
   - `seat:lock:*`
   - `lock:user:*`
   - `lock:order:*`
   - `lock:expire:*`
   - `stream:lock_accepted:*`

这层保证了：

- 同一座位不会被并发重复锁成功
- 同一用户在同一场次不会同时持有多个未终态锁单
- 锁座成功与“后续待桥接事件记录”是同一个原子操作

## 5.2 `lockId` 用于锁归属校验

两个关键脚本：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/unlock_seat.lua](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/unlock_seat.lua)
- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/confirm_purchase.lua](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/confirm_purchase.lua)

两者都依赖：

- `userId + lockId`

进行锁归属判断。

这意味着：

- 不是锁拥有者的重复释放，不会误删别人的锁
- 不是锁拥有者的重复确认购买，不会把别人的座位误标为已售

## 5.3 Redis 锁单状态推进使用“期望状态匹配”

状态推进脚本：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/update_lock_order_status.lua](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-common-redis/src/main/resources/lua/update_lock_order_status.lua)

实现方式：

1. 先读取当前 `lock:order:{orderNo}.status`
2. 只有当前状态命中 `expectedStatuses` 才允许推进
3. 不命中则直接返回 `0`

这相当于 Redis 版本的 CAS。

作用：

- 避免重复消息把锁单状态从终态再改回处理中
- 避免支付、取消、超时三条链路互相覆盖

## 5.4 Redis Stream 桥接天然接受重复投递

桥接任务：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockAcceptedBridgeTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockAcceptedBridgeTask.java)

当前重试机制：

1. 新消息用 `readGroup()` 消费
2. pending 消息会被 `getPendingInfo()` 观察
3. 空闲超过阈值的 pending 消息会被 `autoClaim()` 抢回
4. 只有成功发到 RocketMQ 并推进 Redis 锁单状态后，才 `ACK`

因此：

- 桥接链路天然是“至少一次”
- 重复桥接是允许发生的
- 后续 `order-service` 必须做幂等消费

## 5.5 秒杀侧支付成功消费是重复安全的

消费者：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderPaidConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderPaidConsumer.java)

幂等点：

1. 先查 `seat_locks` 是否已经全部是 `PAID`
   - 如果已经是，直接返回
2. 再执行 `confirmPurchase()`
   - 只有归属锁的用户才能确认
3. 最后推进 Redis 锁单到 `PAID`

所以这个消费者即使重复消费，也不会反复制造不同结果。

## 5.6 秒杀侧取消消费允许 RocketMQ 重试

消费者：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCancelConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCancelConsumer.java)

当前策略：

- 处理失败直接抛异常，允许 MQ 重试

重复安全来自：

1. `unlock_seat.lua` 对锁归属做校验
2. Redis 锁单状态推进要求命中期望状态
3. `seat_locks` 更新是按条件更新

## 5.7 恢复任务是“重试的最后兜底”

恢复任务：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockOrderRecoveryTask.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockOrderRecoveryTask.java)

当前作用：

1. 扫描 `lock:expire:{sessionId}`
2. 找出长时间停留在 `LOCKED` / `ORDER_CREATING` 的锁单
3. 如果正式订单已经存在，则补推进为 `ORDER_CREATED`
4. 如果已经过期且未建单，则释放座位并标记 `TIMEOUT`

所以当前系统不是只靠实时消息链路，而是默认允许“某一环掉链子”，再由恢复任务补收敛。

---

## 6. 订单服务的重试与幂等

## 6.1 `orderNo` 是订单服务主幂等键

当前订单表 DDL：

- [/home/duanyan/project/taopiaopiao-backend/sql/ddl_order.sql](/home/duanyan/project/taopiaopiao-backend/sql/ddl_order.sql)

其中：

- `order_no VARCHAR(64) NOT NULL UNIQUE`

这意味着：

- 数据库层已经把 `orderNo` 设成正式订单唯一键

## 6.2 `LOCK_ACCEPTED` 消费者先查重，再发事务消息

消费者：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/LockAcceptedConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/LockAcceptedConsumer.java)

幂等点：

1. 先按 `orderNo` 查询正式订单
2. 若已存在：
   - 不重复建单
   - 只补发 `ORDER_CREATED_INTERNAL`
3. 若锁单已过期：
   - 不建单
   - 直接走超时释放
4. 只有不存在且未过期，才发事务半消息

因此：

- 即使 Redis Stream 桥接重复投递 `LOCK_ACCEPTED`
- 也不会重复创建正式订单

## 6.3 事务消息本地事务仍然先做订单存在检查

事务监听器：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/listener/OrderTransactionListener.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/listener/OrderTransactionListener.java)

在 `executeLocalTransaction()` 里：

1. 再次按 `orderNo` 查订单
2. 如果订单已存在：
   - 补发 `ORDER_CREATED_INTERNAL`
   - 直接回滚重复半消息
3. 否则才插入正式 `UNPAID` 订单
4. 插入后再发延时 `TIMEOUT_CHECK`
5. 返回 `UNKNOWN`，等待 RocketMQ 后续回查

这层设计说明：

- 幂等不是只压在消费者入口一层
- 即使事务半消息重复到达，本地事务阶段也会再兜一层

## 6.4 事务回查本身就是“重试机制”

同一个事务监听器里的 `checkLocalTransaction()` 是当前最典型的后端自动重试逻辑。

行为是：

1. RocketMQ 在事务状态为 `UNKNOWN` 时会重复回查
2. 每次回查：
   - 先查本地订单是否存在
   - 再查订单状态是否已终态
   - 若仍是 `UNPAID`，再调用 payment-system 查询支付状态
3. 根据支付结果返回：
   - `COMMIT`
   - `ROLLBACK`
   - `UNKNOWN`

也就是说：

- 支付结果没有准备好时，不会立刻失败
- 会继续回查，直到得到稳定结论

这是当前项目最明确的一条“异步重试直到条件满足”的后端链路。

## 6.5 `ORDER_PAID` 消费者按状态条件更新

消费者：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderPaidConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderPaidConsumer.java)

幂等点：

1. 先查订单是否存在
2. 如果已是 `PAID`，直接返回
3. 如果不是 `UNPAID`，直接跳过，不覆盖终态
4. 真正更新时走：
   - `markPaidIfUnpaid(orderNo)`

对应 SQL：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/resources/mapper/OrderMapper.xml](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/resources/mapper/OrderMapper.xml)

SQL 约束是：

- `WHERE order_no = ? AND status = 1`

这就是标准条件更新幂等。

## 6.6 超时检查消费者既是裁决器，也是重试点

消费者：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java)

行为：

1. 若订单已终态，直接返回
2. 查询支付系统
3. 若已支付：
   - `markPaidIfUnpaid`
   - 成功后补发 `ORDER_PAID`
4. 若未支付：
   - `markTimeoutIfUnpaid`
   - 成功后发取消消息
5. 查询支付失败或更新失败则抛异常，让 MQ 重试

这里的重试安全来自：

- `markPaidIfUnpaid`
- `markTimeoutIfUnpaid`
- 终态先判断

所以这条链路可以安全重试，不会把已支付订单再打成超时，也不会把已超时订单再打成待支付。

## 6.7 用户取消订单依赖前置状态检查

接口实现：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/service/impl/OrderServiceImpl.java](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/service/impl/OrderServiceImpl.java)

当前逻辑：

1. 先查询订单
2. 只允许 `UNPAID` 才能取消
3. 先把本地订单更新为 `CANCELLED`
4. 再异步发取消消息

这条链路当前的幂等更偏“应用层前置判断”，不是数据库条件更新 CAS。

所以准确表述是：

- 当前它具备业务前置防重
- 但严格程度不如 `markPaidIfUnpaid` / `markTimeoutIfUnpaid` 那类条件更新

## 6.8 订单详情查询本身就是异步重试友好接口

订单详情查询在：

- `/client/orders/{orderNo}`

其实现会按这个顺序兜底：

1. 正式 `orders`
2. Redis processing cache
3. Redis lock-order aggregate
4. `seckill-service` 内部兜底

所以它天然适合被前端轮询重试，不会因为正式订单还没落库就直接返回“找不到订单”。

---

## 7. 支付系统的重试与幂等

支付系统实现：

- [/home/duanyan/project/taopiaopiao-payment-system/src/main/java/com/duanyan/payment/service/PaymentService.java](/home/duanyan/project/taopiaopiao-payment-system/src/main/java/com/duanyan/payment/service/PaymentService.java)

## 7.1 创建支付记录按 `orderNo` 幂等

实现方式：

- `orderNoStore.computeIfAbsent(orderNo, ...)`

这意味着：

- 同一个 `orderNo` 重复调用 `createPayment()`
- 不会生成多条支付记录
- 会返回同一个支付单

## 7.2 查询支付状态是纯读，可被反复调用

`queryPayment(orderNo)` 是纯查询。

所以它可以被：

- 前端订单详情补齐支付信息时反复查询
- 事务回查时反复查询
- 超时检查时再次查询

## 7.3 模拟支付成功也做了幂等保护

`simulateSuccess(orderNo)` 当前行为：

1. 如果订单不存在，返回 false
2. 如果已经成功，直接返回 true
3. 否则改状态为 `SUCCESS`

所以模拟支付成功接口也是可重复调用的。

---

## 8. RocketMQ 发送与消费的重试边界

## 8.1 发送侧

当前 `order-service` RocketMQ producer 配置在：

- [/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/resources/application.yml](/home/duanyan/project/taopiaopiao-backend/taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/resources/application.yml)

其中：

- `send-message-timeout: 3000`
- `retry-times-when-send-failed: 2`

所以发送失败时，RocketMQ producer 侧本身就可能做有限重试。

这也是为什么消费端必须幂等。

## 8.2 消费侧

当前多个消费者在处理异常时都会直接抛出异常：

- `OrderPaidConsumer`
- `OrderTimeoutCheckConsumer`
- `OrderCancelConsumer`
- `OrderCreatedInternalConsumer`

这意味着：

- 当前系统默认接受 RocketMQ 对失败消息重新投递
- 并通过状态判断、条件更新、锁归属校验来保障重复消费安全

---

## 9. 当前各层“重试”和“幂等”的边界

这里最容易混淆，所以单独写清楚。

### 9.1 前端

- 有自动重试：
  - 订单详情轮询
- 没有自动重试：
  - 锁座
  - 取消订单
  - 删除订单
- 页面级防重：
  - 支付按钮禁用

### 9.2 OpenResty

- 有短时防重复：
  - dedupe
  - terminal hold
  - recent unavailable hold
- 没有最终业务幂等：
  - 不以 `requestId` 判定是否“这单已经执行业务”

### 9.3 Redis / 秒杀服务

- 有最终热点锁座幂等与防冲突：
  - Lua 原子校验
  - 用户锁索引
  - 座位锁归属校验
  - 状态推进 CAS

### 9.4 订单服务

- 有正式订单幂等：
  - `orderNo UNIQUE`
  - 先查重再建单
  - 条件更新防止状态覆盖

### 9.5 RocketMQ

- 接受重复发送、重复投递、重复回查
- 不负责帮业务做幂等
- 幂等责任全部在业务代码

### 9.6 payment-system

- `orderNo` 级别幂等创建
- 允许被反复查询

---

## 10. 当前最重要的事实

1. 当前项目最核心的业务幂等键是 `orderNo`，不是 `requestId`。
2. 当前锁座热点安全主要靠 Redis Lua，不是靠前端或 OpenResty。
3. 当前 MQ 链路默认接受至少一次投递，所以消费者必须幂等。
4. 当前支付确认链路的“重试”主角不是锁座接口，而是：
   - 事务回查
   - 超时检查
   - 前端订单轮询
   - Redis 恢复任务
5. 当前项目的稳定性来自多层小幂等叠加，而不是单点万能幂等。

---

## 11. 一句话总结

当前项目的设计可以概括为：

- 前端负责有限轮询和页面级防重
- OpenResty 负责入口短时防重复
- Redis Lua 负责热点锁座原子性
- `orderNo` 负责跨服务主幂等
- SQL 条件更新负责终态不回滚
- RocketMQ 重复投递由消费者幂等吸收
- 恢复任务负责最终收敛

这就是当前“为什么可以允许重试，但又不会轻易把状态打乱”的根本原因。
