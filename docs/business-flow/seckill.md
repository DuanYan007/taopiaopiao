# Seckill Flow

## Main Goal
The hotspot path accepts lock requests fast, moves formal order creation out of the synchronous request, and lets payment and seat-side effects converge through MQ and recovery tasks.

## Lock Seat Flow
1. OpenResty intercepts `/api/seckill/lock` and applies duplicate suppression, token bucket, and inflight gate for `sessionId=1`.
2. Gateway forwards `/seckill/lock` to `taopiaopiao-seckill-service`.
3. `SeckillController` reads trusted `X-User-Id` and `X-Request-Id`.
4. `SeckillServiceImpl` validates frontend `eventId` against the Redis `sessionId -> eventId` snapshot.
5. `SeckillServiceImpl` calls Redis Lua to lock seats and record the Redis lock-order aggregate, user lock index, expire index, and stream payload in one acceptance step.
6. The lock-seat response returns `lockId`, `orderNo`, `expireTime`, `orderStatus=PROCESSING`, `paymentStatus=NOT_READY`, and `nextAction=POLL_ORDER`.
7. `RedisLockAcceptedBridgeTask` consumes Redis Stream `stream:lock_accepted:{sessionId}`, sends `LOCK_ACCEPTED` to RocketMQ, and advances the Redis lock-order aggregate to `ORDER_CREATING`.
8. `order-service` consumes `LOCK_ACCEPTED` and asynchronously starts formal order creation.
9. `RedisLockOrderRecoveryTask` scans the Redis expire index, catches up accepted locks whose formal orders already exist, and releases expired locks that never converged into formal orders.
10. `RedisLockOrderFlushTask` and `RedisSeatLockFlushTask` asynchronously persist Redis-side acceptance and seat audit data into MySQL `lock_orders` and `seat_locks`.

Key code:
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/controller/SeckillController.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/service/impl/SeckillServiceImpl.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockAcceptedBridgeTask.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockOrderRecoveryTask.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisLockOrderFlushTask.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisSeatLockFlushTask.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/LockAcceptedConsumer.java`
- `taopiaopiao-common-redis/src/main/resources/lua/lock_seat_and_record_order.lua`

## Pay Flow
1. `LockAcceptedConsumer` sends the transactional half message used by the order-create and paid-confirmation chain.
2. `OrderTransactionListener` inserts the local unpaid order, publishes `ORDER_CREATED_INTERNAL`, and sends delayed `TIMEOUT_CHECK`.
3. `seckill-service` consumes `ORDER_CREATED_INTERNAL` and marks the Redis lock-order aggregate as `ORDER_CREATED`.
4. The frontend polls `/client/orders/{orderNo}` after lock success. `OrderServiceImpl.getOrderByNo()` queries in this order:
   - formal `orders`
   - Redis processing cache
   - Redis lock-order aggregate
   - `seckill-service` internal fallback
5. When the formal order exists and no payment record exists, `order-service` lazily creates the mock payment and returns `paymentStatus=READY` plus `payUrl`.
6. Before timeout, Broker transaction checks keep querying payment state and commit `ORDER_PAID` only when payment is confirmed.
7. At the timeout point, `order-service` handles delayed `TIMEOUT_CHECK` and does the final paid-vs-timeout adjudication.
8. `order-service` consumer updates the formal order to `PAID` after `ORDER_PAID`.
9. `seckill-service` consumer confirms Redis seats as sold, updates `seat_locks` if audit rows already exist, and advances the lock-order aggregate to `PAID`.

Key code:
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/LockAcceptedConsumer.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/listener/OrderTransactionListener.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/producer/OrderCreatedInternalProducer.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/service/impl/OrderServiceImpl.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderPaidConsumer.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCreatedInternalConsumer.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderPaidConsumer.java`

## Cancel Flow
1. User action or `order-service` timeout-check emits the final cancel message.
2. `order-service` updates formal order status to `TIMEOUT` or `CANCELLED`.
3. `seckill-service` releases Redis seats and advances the lock-order aggregate to `TIMEOUT` or `CANCELLED`.
4. `RedisSeatLockFlushTask` and consumer updates keep MySQL seat audit rows converged with Redis-side final state.

Key code:
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCancelConsumer.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/task/RedisSeatLockFlushTask.java`

## Observation
- `SeckillBacklogSnapshotTask` logs Redis expire queue size, Redis Stream size and pending count, lock-order backlog, and bridge/recovery counters for pressure-test analysis.
