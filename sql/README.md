# SQL 目录说明

当前 `sql/` 目录只保留现有架构需要的表结构、增量脚本和少量历史清理脚本。

## 主要 DDL

- `ddl_venues.sql`: 场馆表
- `ddl_events.sql`: 演出表
- `ddl_sessions.sql`: 场次表
- `ddl_seat_templates.sql`: 座位模板表
- `ddl_seats.sql`: 场次座位表
- `ddl_order.sql`: 订单表，`seat_ids` 使用 JSON 数组
- `ddl_admin_users.sql`: 管理员表

## 增量脚本

- `20260408_lock_model_upgrade.sql`: 早期锁模型升级脚本
- `20260420_drop_redis_only_legacy_lock_tables.sql`: 删除旧版 `seat_locks` / `lock_orders`
- `seckill_service.sql`: 历史兼容 DDL，仅用于旧环境参考

## 初始化顺序

```bash
mysql -u root -p taopiaopiao < sql/ddl_venues.sql
mysql -u root -p taopiaopiao < sql/ddl_events.sql
mysql -u root -p taopiaopiao < sql/ddl_sessions.sql
mysql -u root -p taopiaopiao < sql/ddl_seat_templates.sql
mysql -u root -p taopiaopiao < sql/ddl_seats.sql
mysql -u root -p taopiaopiao < sql/ddl_order.sql
mysql -u root -p taopiaopiao < sql/ddl_admin_users.sql
```

## 现网对齐

如果本地库来自旧版本，先执行下面的清理脚本，再按当前初始化顺序建库：

```bash
mysql -u root -p taopiaopiao < sql/20260420_drop_redis_only_legacy_lock_tables.sql
```

## 测试数据

- `init_venues.sql`: 场馆测试数据
- `verify_sessions.sql`: 校验场次与座位初始化结果
- `test/`: 压测或临时验证 SQL

## 当前数据约定

- `orders.seat_ids` 是 JSON 字符串数组，例如 `["4","5"]`
- Redis 里的锁单聚合才是当前锁座真相源
- 旧版 `seat_locks` / `lock_orders` 审计模型已废弃
