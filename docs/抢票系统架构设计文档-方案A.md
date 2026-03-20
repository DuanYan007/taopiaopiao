# 抢票系统架构设计文档（方案A）

## 文档信息

| 项目 | 内容 |
|------|------|
| 文档版本 | v1.0 |
| 创建日期 | 2026-03-19 |
| 作者 | duanyan |
| 项目名称 | 淘票票后端 - 抢票系统 |

---

## 一、架构概述

### 1.1 设计原则

- **Redis 为主**：座位实时状态以 Redis 为准，保证高并发性能
- **异步持久化**：通过 RocketMQ 异步同步数据，解耦核心流程
- **最终一致性**：允许短暂的数据不一致，通过对账机制保证最终一致
- **幂等性保证**：所有消息消费和状态更新支持幂等

### 1.2 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              整体架构                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐      ┌──────────┐       │
│  │  用户端  │ ───→ │ Gateway  │ ───→ │SeckillSvc│ ───→ │  Redis   │       │
│  └──────────┘      └──────────┘      └──────────┘      └──────────┘       │
│                           │                       │                         │
│                           ▼                       ▼                         │
│                    ┌──────────┐            ┌──────────┐                    │
│                    │OrderSvc  │ ─────────→│ RocketMQ │                    │
│                    └──────────┘            └──────────┘                    │
│                           │                       │                         │
│                           ▼                       ▼                         │
│                    ┌──────────┐            ┌──────────┐                    │
│                    │SessionSvc│←───────────│Consumer  │                    │
│                    │   DB     │            └──────────┘                    │
│                    └──────────┘                       │                     │
│                           │                         ▼                      │
│                           │                  ┌──────────┐                  │
│                           └─────────────────→│ seat_locks│                  │
│                                              │   seats   │                  │
│                                              └──────────┘                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 核心组件职责

| 组件 | 职责 |
|------|------|
| **Redis** | 座位实时状态存储（0=可选，1=已锁定，2=已售出） |
| **RocketMQ** | 异步消息队列，解耦核心流程 |
| **seat_locks 表** | 座位锁定记录，持久化备份 |
| **seats 表** | 座位最终状态（available/sold） |
| **orders 表** | 订单记录 |

---

## 二、数据模型设计

### 2.1 seats 表（座位表）

```sql
CREATE TABLE seats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    seat_template_id BIGINT,
    template_seat_id VARCHAR(50),
    seat_row VARCHAR(20) NOT NULL,
    seat_column VARCHAR(20) NOT NULL,
    seat_number VARCHAR(50) NOT NULL,
    area VARCHAR(50),
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'available' COMMENT 'available=可选, sold=已售出',
    locked_by BIGINT COMMENT '锁定者ID（保留字段，暂不使用）',
    locked_until DATETIME COMMENT '锁定到期时间（保留字段，暂不使用）',
    order_id BIGINT COMMENT '订单ID',
    order_no VARCHAR(64) COMMENT '订单号',
    metadata JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_seat_number (seat_number),
    INDEX idx_status (status),
    INDEX idx_locked_until (locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**状态说明**：
- `available`：可选（默认状态）
- `sold`：已售出（支付成功后更新）

### 2.2 seat_locks 表（座位锁定记录表）

```sql
CREATE TABLE seat_locks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    seat_id VARCHAR(50) NOT NULL,
    seat_row INT NOT NULL,
    seat_col INT NOT NULL,
    lock_time BIGINT NOT NULL COMMENT '锁定时间戳（毫秒）',
    expire_time BIGINT NOT NULL COMMENT '过期时间戳（毫秒）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0=已释放, 1=已锁定, 2=已支付',
    order_no VARCHAR(64) COMMENT '关联订单号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**状态说明**：
- `0`：RELEASED（已释放）
- `1`：LOCKED（已锁定）
- `2`：PAID（已支付）

### 2.3 orders 表（订单表）

```sql
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    seat_ids JSON NOT NULL COMMENT '座位ID列表',
    seat_count INT NOT NULL COMMENT '座位数量',
    unit_price DECIMAL(10,2) NOT NULL COMMENT '单价',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '总金额',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=未支付, 2=已支付, 3=已取消, 4=已退款, 5=超时取消',
    pay_time DATETIME COMMENT '支付时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间（创建时间+15分钟）',
    cancel_time DATETIME COMMENT '取消时间',
    refund_time DATETIME COMMENT '退款时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**订单状态（OrderStatus）**：
| 状态码 | 枚举值 | 说明 |
|--------|--------|------|
| 1 | UNPAID | 未支付 |
| 2 | PAID | 已支付 |
| 3 | CANCELLED | 已取消（用户主动） |
| 4 | REFUNDED | 已退款 |
| 5 | TIMEOUT | 超时取消 |

### 2.4 Redis 数据结构

| Key 类型 | 格式 | 值类型 | 说明 |
|----------|------|--------|------|
| 座位状态 | `seat:{sessionId}:{seatId}` | String | 0=可选，1=已锁定，2=已售出 |
| 用户锁座 | `user:{userId}:locks` | Hash | field=seatId, value=1 |
| 场次座位集合 | `session:{sessionId}:seats` | Set | 所有座位ID |
| 售罄标志 | `session:{sessionId}:soldout` | String | "1"表示售罄 |

---

## 三、核心流程设计

### 3.1 锁座流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              锁座流程                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  用户请求：POST /seckill/lock                                                │
│  { sessionId, userId, seatIds: ["A-01", "A-02"], expireSeconds: 900 }        │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤1】Redis Lua 原子性锁座                                        │   │
│  │ 执行 lock_seat.lua 脚本：                                            │   │
│  │   • 检查座位是否存在                                                │   │
│  │   • 检查座位状态是否为 0（可选）                                     │   │
│  │   • 检查用户是否重复锁定                                            │   │
│  │   • 设置 seat:{sessionId}:{seatId} = 1（TTL=900秒）                │   │
│  │   • 添加到 user:{userId}:locks                                      │   │
│  │   • 返回：0=成功, 1=不存在, 2=不可用, 3=重复锁定                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓ 成功                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤2】插入 seat_locks 记录                                        │   │
│  │ INSERT INTO seat_locks (session_id, user_id, seat_id, seat_row,     │   │
│  │     seat_col, lock_time, expire_time, status, order_no)             │   │
│  │ VALUES (..., 1, NULL)  -- status=LOCKED, orderNo 暂时为空           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤3】调用 SessionClient 获取场次信息（获取价格）                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤4】调用 OrderClient 创建待支付订单                             │   │
│  │ INSERT INTO orders (order_no, user_id, session_id, event_id,        │   │
│  │     seat_ids, seat_count, unit_price, total_amount, status,         │   │
│  │     expire_time)                                                    │   │
│  │ VALUES (..., 1, NOW() + 15MINUTE)  -- status=UNPAID                │   │
│  │                                                                     │   │
│  │ 同时发送延时消息：                                                   │   │
│  │ message.setDelayTimeLevel(15分钟)                                   │   │
│  │ messageBody: { orderNo, userId, sessionId, seatIds }                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓ 成功                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤5】更新 seat_locks 的 orderNo                                 │   │
│  │ UPDATE seat_locks SET order_no = ?                                  │   │
│  │ WHERE session_id=? AND user_id=? AND seat_id=?                      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  返回：{ success: true, orderNo: "xxx", lockedSeats: [...] }               │
│                                                                             │
│  【回滚机制】如果步骤3或4失败：                                               │
│    • Redis 释放座位（恢复状态为 0）                                        │
│    • 更新 seat_locks 状态为 RELEASED                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 支付流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              支付流程                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  用户请求：POST /client/orders  { orderNo: "xxx" }                           │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤1】查询订单，校验状态                                            │   │
│  │ SELECT * FROM orders WHERE order_no = ? AND user_id = ?              │   │
│  │                                                                     │   │
│  │ 校验：                                                               │   │
│  │   • 订单存在？                                                       │   │
│  │   • 状态是否为 UNPAID（1）？                                          │   │
│  │   • 是否过期？（expire_time > NOW()）                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤2】发送 RocketMQ 半消息                                        │   │
│  │ Topic: PAYMENT_TOPIC                                                │   │
│  │ Tag: PAY_SUCCESS                                                    │   │
│  │ Keys: orderNo                                                       │   │
│  │ Body: { orderNo, userId, sessionId, seatIds, amount, payTime }      │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤3】执行本地事务（支付逻辑）                                     │   │
│  │                                                                     │   │
│  │ 3.1 调用支付接口（第三方支付）                                        │   │
│  │                                                                     │   │
│  │ 3.2 更新订单状态：                                                   │   │
│  │     UPDATE orders                                                   │   │
│  │     SET status = 2, pay_time = NOW()                                │   │
│  │     WHERE order_no = ?                                              │   │
│  │                                                                     │   │
│  │ 3.3 Redis 更新（confirm_purchase.lua）：                             │   │
│  │     • 从 user:{userId}:locks 删除座位                                │   │
│  │     • 设置 seat:{sessionId}:{seatId} = 2（已售出）                   │   │
│  │                                                                     │   │
│  │ 3.4 更新 seat_locks：                                                │   │
│  │     UPDATE seat_locks SET status = 2 WHERE order_no = ?              │   │
│  │                                                                     │   │
│  │ 如果成功：提交消息                                                  │   │
│  │ 如果失败：回滚消息                                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤4】Broker 回查（异常情况）                                     │   │
│  │ 如果事务状态未知，Broker 会回查：                                    │   │
│  │   • 查询订单状态                                                    │   │
│  │   • 如果 status=PAID，确认提交                                      │   │
│  │   • 如果 status=UNPAID，回滚                                        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【消费者 - SessionService】处理支付成功消息                          │   │
│  │ Topic: PAYMENT_TOPIC                                               │   │
│  │ Tag: PAY_SUCCESS                                                   │   │
│  │                                                                     │   │
│  │ 1. 幂等性校验：检查 seats 表是否已更新                               │   │
│  │    SELECT * FROM seats WHERE order_no = ?                           │   │
│  │                                                                     │   │
│  │ 2. 更新 seats 表：                                                  │   │
│  │    UPDATE seats                                                     │   │
│  │    SET status = 'sold', order_no = ?                                │   │
│  │    WHERE seat_number IN (...) AND session_id = ?                    │   │
│  │                                                                     │   │
│  │ 3. 确认消息消费                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  返回：{ orderNo, status: PAID, payTime }                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 取消订单流程（用户主动）

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         用户主动取消订单流程                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  用户请求：POST /client/orders/{orderNo}/cancel                               │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤1】查询订单，校验状态                                            │   │
│  │ SELECT * FROM orders WHERE order_no = ? AND user_id = ?              │   │
│  │                                                                     │   │
│  │ 校验：必须是 UNPAID 状态                                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤2】发送取消消息（使用普通消息，保证顺序）                        │   │
│  │ Topic: ORDER_TOPIC                                                 │   │
│  │ Tag: CANCEL_ORDER                                                  │   │
│  │ Keys: orderNo                                                      │   │
│  │ Body: { orderNo, userId, sessionId, seatIds, reason: 'USER' }       │   │
│  │                                                                     │   │
│  │ 同时本地更新：                                                       │   │
│  │ UPDATE orders SET status = 3, cancel_time = NOW()                   │   │
│  │ WHERE order_no = ?                                                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【消费者 - SeckillService】处理取消消息                               │   │
│  │                                                                     │   │
│  │ 1. 幂等性校验：检查订单状态                                          │   │
│  │    如果已经是 CANCELLED/TIMEOUT，跳过                                │   │
│  │                                                                     │   │
│  │ 2. Redis 释放座位（unlock_seat.lua）：                                │   │
│  │    • 从 user:{userId}:locks 删除座位                                 │   │
│  │    • 设置 seat:{sessionId}:{seatId} = 0（恢复可选）                   │   │
│  │                                                                     │   │
│  │ 3. 更新 seat_locks：                                                  │   │
│  │    UPDATE seat_locks SET status = 0 WHERE order_no = ?               │   │
│  │                                                                     │   │
│  │ 4. 确认消息消费                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  返回：{ success: true }                                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.4 超时订单取消流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         超时订单取消流程（延时消息）                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【触发时机】锁座成功后，发送 15 分钟延时消息                                   │
│  （在 3.1 锁座流程的步骤4中同时发送）                                          │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【15分钟后】消费者收到延时消息                                         │   │
│  │ Topic: ORDER_TOPIC                                                 │   │
│  │ Tag: CANCEL_ORDER                                                  │   │
│  │ Body: { orderNo, userId, sessionId, seatIds, reason: 'TIMEOUT' }    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤1】幂等性校验                                                   │   │
│  │ SELECT * FROM orders WHERE order_no = ?                              │   │
│  │                                                                     │   │
│  │ 如果 status = PAID（用户已支付）：                                    │   │
│  │   → 跳过处理，确认消息消费                                           │   │
│  │                                                                     │   │
│  │ 如果 status = CANCELLED（已取消）：                                   │   │
│  │   → 跳过处理，确认消息消费                                           │   │
│  │                                                                     │   │
│  │ 如果 status = UNPAID（未支付）：                                      │   │
│  │   → 继续执行取消流程                                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 【步骤2】执行取消流程                                                 │   │
│  │                                                                     │   │
│  │ 2.1 Redis 释放座位：                                                 │   │
│  │     执行 unlock_seat.lua 脚本                                        │   │
│  │                                                                     │   │
│  │ 2.2 更新 seat_locks：                                                │   │
│  │     UPDATE seat_locks SET status = 0 WHERE order_no = ?              │   │
│  │                                                                     │   │
│  │ 2.3 更新 orders：                                                    │   │
│  │     UPDATE orders SET status = 5 WHERE order_no = ?                  │   │
│  │     -- 5 = TIMEOUT 超时取消                                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    ↓                                        │
│  确认消息消费                                                                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 四、RocketMQ 消息设计

### 4.1 Topic 定义

| Topic | 用途 | 消费者 |
|-------|------|--------|
| `PAYMENT_TOPIC` | 支付成功消息 | SessionService（更新 seats 表） |
| `ORDER_TOPIC` | 订单取消消息 | SeckillService（释放座位） |

### 4.2 消息格式

#### 4.2.1 支付成功消息

```json
{
  "topic": "PAYMENT_TOPIC",
  "tag": "PAY_SUCCESS",
  "keys": "ORDER_NO_20260319123456",
  "body": {
    "orderNo": "ORDER_NO_20260319123456",
    "userId": 10001,
    "sessionId": 1,
    "seatIds": ["A-01", "A-02"],
    "amount": 5120.00,
    "payTime": "2026-03-19T12:34:56"
  }
}
```

#### 4.2.2 订单取消消息

```json
{
  "topic": "ORDER_TOPIC",
  "tag": "CANCEL_ORDER",
  "keys": "ORDER_NO_20260319123456",
  "body": {
    "orderNo": "ORDER_NO_20260319123456",
    "userId": 10001,
    "sessionId": 1,
    "seatIds": ["A-01", "A-02"],
    "reason": "USER"  // USER=用户取消, TIMEOUT=超时取消
  }
}
```

### 4.3 消息类型

| 消息用途 | 消息类型 | 延时等级 |
|----------|----------|----------|
| 支付成功 | 事务消息 | 无 |
| 订单取消（用户） | 普通消息 | 无 |
| 订单取消（超时） | 延时消息 | 15分钟 |

---

## 五、幂等性设计

### 5.1 支付成功消息幂等

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         消费者幂等性处理                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【方法1：基于订单号的幂等】                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ 1. 消息消费前检查：                                                   │   │
│  │    SELECT * FROM seats WHERE order_no = ?                            │   │
│  │    -- 如果已有记录，说明已处理，跳过                                   │   │
│  │                                                                     │   │
│  │ 2. 使用 INSERT ... ON DUPLICATE KEY UPDATE：                          │   │
│  │    UPDATE seats SET status = 'sold' WHERE order_no = ?               │   │
│  │    -- 即使重复消费，结果也相同                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  【方法2：消息去重表】                                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ CREATE TABLE mq_message_log (                                        │   │
│  │   id BIGINT AUTO_INCREMENT PRIMARY KEY,                             │   │
│  │   message_key VARCHAR(64) UNIQUE,  -- orderNo                        │   │
│  │   topic VARCHAR(64),                                                │   │
│  │   tag VARCHAR(64),                                                  │   │
│  │   processed_at DATETIME,                                            │   │
│  │   INDEX idx_message_key (message_key)                               │   │
│  │ );                                                                  │   │
│  │                                                                     │   │
│  │ -- 消费前插入，如果已存在则跳过                                       │   │
│  │ INSERT IGNORE INTO mq_message_log (message_key, topic, tag, ...)    │   │
│  │ VALUES (?, 'PAYMENT_TOPIC', 'PAY_SUCCESS', ...)                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 取消消息幂等

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         取消消息幂等性处理                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. 检查订单状态：                                                           │
│     SELECT status FROM orders WHERE order_no = ?                            │
│                                                                             │
│  2. 根据状态决定是否处理：                                                   │
│     ┌──────────┬─────────────────────────────────────────────────────────┐  │
│     │ 订单状态  │ 处理动作                                                │  │
│     ├──────────┼─────────────────────────────────────────────────────────┤  │
│     │ UNPAID   │ 执行取消流程                                          │  │
│     │ PAID     │ 跳过（用户已支付，不能取消）                            │  │
│     │ CANCELLED│ 跳过（已取消）                                          │  │
│     │ TIMEOUT  │ 跳过（已超时取消）                                      │  │
│     └──────────┴─────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、异常处理

### 6.1 支付流程异常处理

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        支付流程异常场景                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【场景1：本地事务执行成功，但消息提交失败】                                   │
│  处理：Broker 回查时会发现订单状态为 PAID，确认提交消息                        │
│                                                                             │
│  【场景2：本地事务执行失败，消息回滚】                                        │
│  处理：订单状态仍为 UNPAID，用户可以重新支付                                  │
│                                                                             │
│  【场景3：消费者更新 seats 表失败】                                          │
│  处理：                                                                     │
│    - 消息消费失败，重试（最多3次）                                           │
│    - 重试仍失败，进入死信队列，人工处理                                      │
│    - 对账任务会检测不一致并修复                                              │
│                                                                             │
│  【场景4：Redis 更新失败】                                                    │
│  处理：本地事务回滚，消息不提交                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Redis 宕机处理

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Redis 宕机恢复策略                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【阶段1：故障检测】                                                          │
│  - 健康检查：定期 ping Redis                                                 │
│  - 告警：Redis 连接失败时告警                                                │
│                                                                             │
│  【阶段2：降级处理】                                                          │
│  - 暂停接受新的锁座请求，返回"系统繁忙，请稍后重试"                            │
│  - 已有订单的支付流程继续（可以不依赖 Redis）                                 │
│                                                                             │
│  【阶段3：Redis 恢复后重建状态】                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ -- 从 seat_locks 重建 Redis 状态                                      │   │
│  │ SELECT * FROM seat_locks                                             │   │
│  │ WHERE status = 1 (LOCKED)                                            │   │
│  │   AND expire_time > UNIX_TIMESTAMP() * 1000                          │   │
│  │                                                                     │   │
│  │ for each record:                                                     │   │
│  │   // 计算剩余 TTL                                                     │   │
│  │   remaining_ttl = (expire_time - current_time) / 1000                 │   │
│  │                                                                     │   │
│  │   // 恢复座位状态                                                     │   │
│  │   Redis.set(seat:{sessionId}:{seatId}, 1, EX=remaining_ttl)          │   │
│  │   Redis.hset(user:{userId}:locks, seatId, 1)                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 七、监控和对账

### 7.1 监控指标

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            监控指标                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【业务指标】                                                                │
│  - 锁座成功率（锁座成功数 / 锁座请求数）                                      │
│  - 支付成功率（支付成功数 / 支付请求数）                                      │
│  - 订单取消率（取消订单数 / 总订单数）                                        │
│  - 超时取消率（超时订单数 / 总订单数）                                        │
│                                                                             │
│  【技术指标】                                                                │
│  - Redis 连接状态、响应时间                                                  │
│  - RocketMQ 消息堆积量、消费延迟                                             │
│  - 数据库连接池使用率、慢查询                                                 │
│                                                                             │
│  【一致性指标】                                                              │
│  - Redis 锁座数量 vs seat_locks LOCKED 数量                                  │
│  - seats 表 sold 数量 vs orders 表 PAID 数量                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 对账任务

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            定时对账任务                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【任务1：Redis 与 seat_locks 对账】（每小时）                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ -- 检查 seat_locks 中状态为 LOCKED 但 Redis 中不存在的记录             │   │
│  │ SELECT sl.* FROM seat_locks sl                                       │   │
│  │ WHERE sl.status = 1                                                  │   │
│  │   AND sl.expire_time > NOW()                                         │   │
│  │   AND NOT EXISTS (SELECT 1 FROM redis_state rs                        │   │
│  │                    WHERE rs.key = CONCAT('seat:', sl.session_id,    │   │
│  │                                              ':', sl.seat_id))       │   │
│  │                                                                     │   │
│  │ -- 修复：恢复 Redis 状态                                              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  【任务2：orders 与 seats 对账】（每天凌晨）                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ -- 检查已支付订单但 seats 表状态不是 sold 的记录                        │   │
│  │ SELECT o.order_no, o.seat_ids, o.status                              │   │
│  │ FROM orders o                                                        │   │
│  │ WHERE o.status = 2 (PAID)                                            │   │
│  │   AND NOT EXISTS (                                                    │   │
│  │     SELECT 1 FROM seats s                                             │   │
│  │     WHERE s.order_no = o.order_no AND s.status = 'sold'               │   │
│  │   )                                                                  │   │
│  │                                                                     │   │
│  │ -- 修复：以 orders 为准，更新 seats 表                                 │   │
│  │ UPDATE seats SET status = 'sold', order_no = ?                       │   │
│  │ WHERE seat_number IN (...)                                           │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 八、接口设计

### 8.1 锁座接口

```
POST /seckill/lock

Request:
{
  "sessionId": 1,
  "userId": 10001,
  "seatIds": ["A-01", "A-02"],
  "expireSeconds": 900
}

Response (Success):
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": true,
    "code": 0,
    "message": "锁座成功",
    "lockedSeats": ["A-01", "A-02"],
    "lockId": "abc123",
    "orderNo": "ORDER_NO_20260319123456"
  }
}

Response (Fail - 座位已被锁定):
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "success": false,
    "code": 2,
    "message": "座位已被锁定或售出"
  }
}
```

### 8.2 支付接口

```
POST /client/orders

Request:
{
  "orderNo": "ORDER_NO_20260319123456"
}

Response (Success):
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "orderNo": "ORDER_NO_20260319123456",
    "status": 2,
    "statusDesc": "已支付",
    "payTime": "2026-03-19 12:34:56"
  }
}
```

### 8.3 取消订单接口

```
POST /client/orders/{orderNo}/cancel

Response:
{
  "code": 200,
  "msg": "操作成功",
  "data": true
}
```

---

## 九、实施计划

### 9.1 实施阶段

| 阶段 | 任务 | 优先级 | 预估工作量 |
|------|------|--------|-----------|
| **P0** | 修复订单状态码不一致问题 | 高 | 0.5天 |
| **P0** | 增加支付流程幂等性校验 | 高 | 1天 |
| **P1** | 集成 RocketMQ | 高 | 2天 |
| **P1** | 改造支付流程为事务消息 | 高 | 2天 |
| **P1** | 实现延时消息超时取消 | 中 | 1天 |
| **P1** | 实现取消订单消息化 | 中 | 1天 |
| **P2** | Redis 宕机恢复机制 | 中 | 1天 |
| **P2** | 监控和对账任务 | 中 | 2天 |
| **P3** | 文档完善和测试 | 低 | 2天 |

### 9.2 风险评估

| 风险 | 影响 | 应对措施 |
|------|------|----------|
| RocketMQ 集成复杂度高 | 可能延期 | 先在测试环境验证，预留缓冲时间 |
| 分布式事务调试困难 | 可能出现数据不一致 | 充分测试，完善对账机制 |
| Redis 宕机影响业务 | 无法锁座 | 实现降级方案，快速恢复 |

---

## 十、附录

### 10.1 Lua 脚本

#### lock_seat.lua（锁座脚本）

```lua
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=seatCount, ARGV[3]=expireSeconds, ARGV[4..]=seats
-- 返回: 0=成功, 1=座位不存在, 2=座位不可用, 3=重复购票

local sessionId = KEYS[1]
local userId = ARGV[1]
local seatCount = tonumber(ARGV[2])
local expireSeconds = tonumber(ARGV[3]) or 300
local userLockKey = "user:" .. userId .. ":locks"

if expireSeconds <= 0 then
    expireSeconds = 300
end

-- 检查重复购票
for i = 4, 4 + seatCount - 1 do
    if redis.call("HEXISTS", userLockKey, ARGV[i]) == 1 then
        return 3
    end
end

-- 执行锁座
for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatKey = "seat:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatKey))

    if current == nil then
        return 1  -- 座位不存在
    end

    if current ~= 0 then
        return 2  -- 座位不可用
    end

    redis.call("SET", seatKey, 1, "EX", expireSeconds)
    redis.call("HSET", userLockKey, seatId, "1")
end

return 0
```

#### unlock_seat.lua（释放座位脚本）

```lua
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=seatCount, ARGV[3..]=seats
-- 返回: 释放的座位数量

local sessionId = KEYS[1]
local userId = ARGV[1]
local seatCount = tonumber(ARGV[2])
local userLockKey = "user:" .. userId .. ":locks"
local unlocked = 0

for i = 3, 3 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatKey = "seat:" .. sessionId .. ":" .. seatId

    if redis.call("HDEL", userLockKey, seatId) == 1 then
        redis.call("SET", seatKey, 0)
        unlocked = unlocked + 1
    end
end

return unlocked
```

#### confirm_purchase.lua（确认购买脚本）

```lua
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=seatCount, ARGV[3..]=seats
-- 返回: 0=成功, 1=无权操作

local sessionId = KEYS[1]
local userId = ARGV[1]
local seatCount = tonumber(ARGV[2])
local userLockKey = "user:" .. userId .. ":locks"

for i = 3, 3 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatKey = "seat:" .. sessionId .. ":" .. seatId

    if redis.call("HDEL", userLockKey, seatId) == 1 then
        redis.call("SET", seatKey, 2)  -- 标记为已售出
    else
        return 1  -- 无权操作
    end
end

return 0
```

### 10.2 状态流转图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            状态流转图                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Redis 座位状态：                                                            │
│  0 (available) ──锁座成功──→ 1 (locked) ──支付成功──→ 2 (sold)              │
│       ↑                              │                                      │
│       └────────释放座位────────────────┘                                      │
│                                                                             │
│  订单状态：                                                                  │
│  1 (UNPAID) ──支付成功──→ 2 (PAID)                                           │
│       │                                                                      │
│       ├───用户取消──→ 3 (CANCELLED)                                          │
│       │                                                                      │
│       └───超时取消──→ 5 (TIMEOUT)                                            │
│                                                                             │
│  seat_locks 状态：                                                           │
│  1 (LOCKED) ──支付成功──→ 2 (PAID)                                           │
│       │                                                                      │
│       └───释放座位──→ 0 (RELEASED)                                           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

**文档结束**
