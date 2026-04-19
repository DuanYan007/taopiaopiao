# SQL 目录说明

当前 `sql/` 目录只保留现有架构需要的表结构和增量脚本。

## 主要 DDL

- `ddl_venues.sql`: 场馆表
- `ddl_events.sql`: 演出表
- `ddl_sessions.sql`: 场次表
- `ddl_seat_templates.sql`: 座位模板表
- `ddl_seats.sql`: 场次座位表
- `ddl_order.sql`: 订单表，`seat_ids` 使用 JSON 数组
- `seckill_service.sql`: 秒杀审计表 `seat_locks`
- `20260417_lock_orders.sql`: 锁单审计表 `lock_orders`
- `ddl_admin_users.sql`: 管理员表

## 增量脚本

- `20260408_lock_model_upgrade.sql`: 早期锁模型升级脚本
- `20260409_fix_lock_id_index.sql`: 修复 `seat_locks.lock_id` 索引
- `20260417_normalize_seat_ids_json.sql`: 统一 `orders.seat_ids` 和 `lock_orders.seat_ids_json` 为字符串 JSON 数组

## 初始化顺序

```bash
mysql -u root -p taopiaopiao < sql/ddl_venues.sql
mysql -u root -p taopiaopiao < sql/ddl_events.sql
mysql -u root -p taopiaopiao < sql/ddl_sessions.sql
mysql -u root -p taopiaopiao < sql/ddl_seat_templates.sql
mysql -u root -p taopiaopiao < sql/ddl_seats.sql
mysql -u root -p taopiaopiao < sql/ddl_order.sql
mysql -u root -p taopiaopiao < sql/seckill_service.sql
mysql -u root -p taopiaopiao < sql/20260417_lock_orders.sql
mysql -u root -p taopiaopiao < sql/ddl_admin_users.sql
```

## 现网对齐

如果本地库来自旧版本，至少补执行下面两个增量脚本：

```bash
mysql -u root -p taopiaopiao < sql/20260409_fix_lock_id_index.sql
mysql -u root -p taopiaopiao < sql/20260417_normalize_seat_ids_json.sql
```

## 测试数据

- `init_venues.sql`: 场馆测试数据
- `verify_sessions.sql`: 校验场次与座位初始化结果
- `test/`: 压测或临时验证 SQL

## 当前数据约定

- `orders.seat_ids` 是 JSON 字符串数组，例如 `["4","5"]`
- `lock_orders.seat_ids_json` 是 JSON 字符串数组，例如 `["4","5"]`
- `seat_locks.seat_id` 保存的是 `seats.id` 的字符串值，不再使用 `row:col`
