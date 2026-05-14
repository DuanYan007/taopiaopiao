# Deploy

## 1. 启动 MySQL 容器

```bash
docker pull mysql:8.4.8

mkdir -p "$HOME/data/mysql/conf" "$HOME/data/mysql/data"

docker run -d \
  --name mysql \
  --restart unless-stopped \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=7566 \
  -v "$HOME/data/mysql/conf":/etc/mysql/conf.d \
  -v "$HOME/data/mysql/data":/var/lib/mysql \
  mysql:8.4.8
```

## 2. 按顺序执行脚本

```bash
docker exec -i mysql mysql -uroot -p7566 < deploy/mysql/ddl.sql
docker exec -i mysql mysql -uroot -p7566 < deploy/mysql/dml.sql
```

## 3. 已有库增量迁移

如果数据库已经按旧结构初始化过，需要按顺序执行以下增量迁移：

```bash
docker exec -i mysql mysql -uroot -p7566 < deploy/mysql/migrate-remove-order-lock-id.sql
docker exec -i mysql mysql -uroot -p7566 < deploy/mysql/migrate-add-order-prepare.sql
```

当前订单链路已经统一使用 `orderNo` 作为 Redis 临时锁 owner token，`orders` 表不再保留 `lock_id` 字段。

当前锁座 + 下单链路已经接入 Seata TCC：

- `seckill-service` Try 只写 Redis 临时锁。
- `order-service` Try 只写 `order_prepare` 预留记录。
- Seat Confirm 只把 Redis 长期状态推进到 `seat:state=1`，表示“已下单未支付”。
- TCC Confirm 时才正式创建 `orders` 的 `UNPAID` 订单并发送 `TIMEOUT_CHECK` 延时消息。
- 真实支付成功后，再由异步支付收敛链路把 `seat:state` 从 `1` 推进到 `2`。
- 若超时或取消，则由取消收敛链路把 `seat:state` 从 `1` 释放回 `0`。
- Seat / Order 两个 TCC 分支都已实现空回滚防护，避免 Cancel 先到时出现悬挂 Try。
- 支付不纳入 Seata 全局事务，仍由支付回调和超时检查异步收敛。
