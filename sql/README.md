# SQL 文件说明

本项目 SQL 文件按照功能分为两类：DDL（表结构定义）和 INIT（测试数据）。

## 文件列表

### DDL - 表结构定义

#### 核心业务表
- `ddl_venues.sql` - 场馆表结构
- `ddl_events.sql` - 演出表结构
- `ddl_order.sql` - 订单表结构
- `seckill_service.sql` - 秒杀锁座表结构
- `ddl_ticket_tiers.sql` - 票档表结构

#### 其他表
- `ddl_seats.sql` - 座位表结构（未实现）
- `ddl_sessions.sql` - 场次表结构（未实现）
- `ddl_admin_users.sql` - 管理员用户表结构

### INIT - 测试数据

- `init_venues.sql` - 场馆测试数据（10条）
- `init_events.sql` - 演出测试数据（15条）
- `init_ticket_tiers.sql` - 票档测试数据（63条，对应15个演出）
- `init_sessions.sql` - 场次测试数据（55条，对应15个演出）
- `init_admin_users.sql` - 管理员账户数据

## 使用顺序

### 1. 创建表结构（按依赖顺序）

```bash
# 1. 创建场馆表
mysql -u root -p taopiaopiao < sql/ddl_venues.sql

# 2. 创建演出表（依赖 venues 表）
mysql -u root -p taopiaopiao < sql/ddl_events.sql

# 3. 创建票档表（依赖 events 表）
mysql -u root -p taopiaopiao < sql/ddl_ticket_tiers.sql

# 4. 创建其他表
mysql -u root -p taopiaopiao < sql/ddl_admin_users.sql
mysql -u root -p taopiaopiao < sql/ddl_seats.sql
mysql -u root -p taopiaopiao < sql/ddl_sessions.sql
```

### 2. 导入测试数据（按依赖顺序）

```bash
# 1. 导入管理员账户
mysql -u root -p taopiaopiao < sql/init_admin_users.sql

# 2. 导入场馆数据
mysql -u root -p taopiaopiao < sql/init_venues.sql

# 3. 导入演出数据（依赖 venues 表）
mysql -u root -p taopiaopiao < sql/init_events.sql

# 4. 导入场次数据（依赖 events 表）
mysql -u root -p taopiaopiao < sql/init_sessions.sql
```

### 一键初始化（全新数据库）

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS taopiaopiao CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 按顺序创建所有表
mysql -u root -p taopiaopiao < sql/ddl_venues.sql
mysql -u root -p taopiaopiao < sql/ddl_events.sql
mysql -u root -p taopiaopiao < sql/ddl_ticket_tiers.sql
mysql -u root -p taopiaopiao < sql/ddl_admin_users.sql

# 导入测试数据
mysql -u root -p taopiaopiao < sql/init_admin_users.sql
mysql -u root -p taopiaopiao < sql/init_venues.sql
mysql -u root -p taopiaopiao < sql/init_events.sql
mysql -u root -p taopiaopiao < sql/init_sessions.sql
mysql -u root -p taopiaopiao < sql/init_ticket_tiers.sql
```

## 重要注意事项

### 增量迁移
- `20260408_lock_model_upgrade.sql`：将锁模型升级为 `seat:state + seat:lock` 设计时需要执行的数据库增量脚本
- `20260409_fix_lock_id_index.sql`：修复 `seat_locks.lock_id` 被错误建成唯一索引的问题，改为普通索引

### 外键约束
- `events.venue_id` → `venues.id`
- `sessions.event_id` → `events.id`（ON DELETE CASCADE）
- `sessions.venue_id` → `venues.id`
- `ticket_tiers.event_id` → `events.id`（ON DELETE CASCADE）
- `seats.session_id` → `sessions.id`

**必须按依赖顺序创建表和导入数据！**

### AUTO_INCREMENT 问题
- 如果需要重置 AUTO_INCREMENT，使用 TRUNCATE 而不是 DELETE
- TRUNCATE 会清空表并重置自增ID

### 数据一致性
- `init_ticket_tiers.sql` 假设 event_id 从 1 开始连续
- 如果 events 表有数据断层，需要先 TRUNCATE 重新导入

## 验证数据

```sql
-- 验证外键关系
SELECT
    e.id AS event_id,
    e.name AS event_name,
    v.id AS venue_id,
    v.name AS venue_name,
    COUNT(tt.id) AS ticket_tier_count
FROM events e
LEFT JOIN venues v ON e.venue_id = v.id
LEFT JOIN ticket_tiers tt ON e.id = tt.event_id
GROUP BY e.id, e.name, v.id, v.name
ORDER BY e.id;
```

## 数据量统计

- 场馆：10条
- 演出：15条
- 场次：55条（每个演出1-8个场次）
- 票档：63条（每个演出3-5个票档）
- 管理员：1条（admin/admin123）

## 常见问题

### 外键约束失败
```
Cannot add or update a child row: a foreign key constraint fails
```
**原因**：引用的ID不存在
**解决**：按依赖顺序导入数据，或使用 TRUNCATE 清空后重新导入

### AUTO_INCREMENT 不连续
**原因**：删除数据导致ID断层
**解决**：
```sql
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE ticket_tiers;
TRUNCATE TABLE events;
SET FOREIGN_KEY_CHECKS = 1;
```
然后重新导入数据
