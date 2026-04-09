# Seckill Flow

## Main Goal
The critical path is: user selects seats, system locks them fast, returns `orderNo` and `payUrl`, then completes payment and downstream side effects with eventual consistency.

## Lock Seat Flow
1. OpenResty intercepts `/api/seckill/lock` and applies duplicate suppression, token bucket, and inflight gate for `sessionId=1`.
2. Gateway forwards `/seckill/lock` to `taopiaopiao-seckill-service`.
3. `SeckillController` reads trusted `X-User-Id` and `X-Request-Id`.
4. `SeckillServiceImpl` calls Redis Lua to lock seats, writes `seat_locks`, then calls `order-service` to create a pending order.
5. The lock-seat response returns `orderNo` and `payUrl` to the frontend.

Key code:
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/controller/SeckillController.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/service/impl/SeckillServiceImpl.java`
- `taopiaopiao-common-redis/src/main/resources/lua/lock_seat.lua`

## Pay Flow
1. `order-service` sends a transactional half message and creates a local unpaid order.
2. Before timeout, Broker transaction checks keep querying payment state and commit `ORDER_PAID` only when payment is confirmed.
3. At the timeout point, `order-service` handles a delayed timeout-check message and does the final paid-vs-timeout adjudication.
4. `order-service` consumer updates the order to `PAID` after `ORDER_PAID`.
5. `seckill-service` consumer confirms Redis seats as sold and marks `seat_locks` as paid.

Key code:
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/listener/OrderTransactionListener.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java`
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderPaidConsumer.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderPaidConsumer.java`

## Cancel Flow
1. User action or `order-service` timeout-check emits `OrderCancelMessage`.
2. `order-service` updates order status to `TIMEOUT` or `CANCELLED`.
3. `seckill-service` releases Redis seats and clears `seat_locks`.

Key code:
- `taopiaopiao-order-service/taopiaopiao-order-service-application/src/main/java/com/duanyan/taopiaopiao/orderservice/application/consumer/OrderTimeoutCheckConsumer.java`
- `taopiaopiao-seckill-service/taopiaopiao-seckill-service-application/src/main/java/com/duanyan/taopiaopiao/seckillservice/application/consumer/OrderCancelConsumer.java`
