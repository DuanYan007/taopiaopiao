# RocketMQ 接入变更说明

> 快速了解接入 RocketMQ 后代码流程的变化

## 一、新增文件总览

### 1.1 新增模块

```
taopiaopiao-common-mq/                    # 新增：MQ 公共模块
├── MqTopic.java                          # Topic 和 Tag 常量定义
└── message/
    ├── PaymentSuccessMessage.java        # 支付成功消息体
    └── OrderCancelMessage.java           # 订单取消消息体
```

### 1.2 各服务新增文件

| 服务 | 新增文件 | 用途 |
|------|----------|------|
| **order-service** | `producer/PaymentSuccessProducer.java` | 发送支付成功消息 |
| **order-service** | `producer/OrderCancelProducer.java` | 发送取消消息 + 延时消息 |
| **session-service** | `consumer/PaymentSuccessConsumer.java` | 消费支付成功消息，更新 seats 表 |
| **seckill-service** | `consumer/OrderCancelConsumer.java` | 消费取消消息，释放 Redis 座位 |

---

## 二、核心流程变化

### 2.1 支付流程变化

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         支付流程 - 接入 MQ 前后对比                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【接入前】同步调用                                                          │
│  OrderService.pay()                                                        │
│      ├── 更新订单状态                                                       │
│      ├── 更新 Redis                                                         │
│      └── 同步调用 SessionService (❌ 耦合严重)                               │
│                                                                             │
│  【接入后】异步解耦                                                          │
│  OrderService.pay()                                                        │
│      ├── 更新订单状态                                                       │
│      ├── 更新 Redis                                                         │
│      └── 发送 MQ 消息 ──────→ PaymentSuccessConsumer (SessionService)       │
│                              └── 更新 seats 表 (异步)                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 订单取消流程变化

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    订单取消流程 - 接入 MQ 前后对比                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【接入前】定时任务轮询                                                      │
│  @Scheduled 任务                                                           │
│      └── 扫描超时订单 → 释放座位 (❌ 实时性差，数据库压力大)                  │
│                                                                             │
│  【接入后】延时消息                                                          │
│  创建订单时 ──→ 发送 15 分钟延时消息                                        │
│                      │                                                      │
│                      └── 15分钟后 ──→ OrderCancelConsumer (SeckillService)  │
│                                      └── 释放座位 (✅ 精准触发，无轮询)      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、关键代码变化

### 3.1 OrderService.pay() 方法

```java
// 接入前：同步调用
@Transactional
public OrderResponse pay(Long userId, CreateOrderRequest request) {
    // ... 更新订单
    // ... 更新 Redis
    sessionClient.markSeatsSold(...);  // ❌ 同步调用，强耦合
    return response;
}

// 接入后：异步消息
@Transactional
public OrderResponse pay(Long userId, CreateOrderRequest request) {
    // ... 更新订单
    // ... 更新 Redis

    // ✅ 发送 MQ 消息，异步更新 seats 表
    PaymentSuccessMessage message = PaymentSuccessMessage.builder()
            .orderNo(order.getOrderNo())
            .userId(userId)
            .sessionId(order.getSessionId())
            .seatIds(seatIds)
            .amount(order.getTotalAmount())
            .payTime(now)
            .build();
    paymentSuccessProducer.sendPaymentSuccessMessage(message);

    return response;
}
```

### 3.2 订单超时取消变化

```java
// 接入前：定时任务（已删除）
@Scheduled(fixedDelay = 60000)
public void cancelTimeoutOrders() {
    // 扫描所有超时订单
    // 逐个处理
    // ❌ 性能差，实时性低
}

// 接入后：延时消息（创建订单时发送）
public OrderResponse createPendingOrder(CreatePendingOrderRequest request) {
    // ... 创建订单

    // ✅ 发送 15 分钟延时消息
    OrderCancelMessage cancelMessage = OrderCancelMessage.builder()
            .orderNo(orderNo)
            .userId(request.getUserId())
            .sessionId(request.getSessionId())
            .seatIds(request.getSeatIds())
            .reason("TIMEOUT")
            .build();
    orderCancelProducer.sendDelayCancelMessage(cancelMessage, 15);

    return response;
}
```

---

## 四、新增配置

### 4.1 Maven 依赖

```xml
<!-- order-service/pom.xml、session-service/pom.xml、seckill-service/pom.xml -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.duanyan</groupId>
    <artifactId>taopiaopiao-common-mq</artifactId>
</dependency>
```

### 4.2 application.yml

```yaml
# order-service/application.yml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: order-service-producer-group

# session-service/application.yml
rocketmq:
  name-server: 127.0.0.1:9876
  consumer:
    group: session-service-consumer-group

# seckill-service/application.yml
rocketmq:
  name-server: 127.0.0.1:9876
  consumer:
    group: seckill-service-consumer-group
```

---

## 五、Topic 和消息流向

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Topic 和消息流向                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  TPP_PAYMENT_TOPIC (支付 Topic)                                             │
│  │                                                                          │
│  │  ┌─ OrderService.pay() ──发送──→ PAY_SUCCESS 标签                        │
│  │                                                      │                   │
│  │                                                      ▼                   │
│  │  └─ SessionService.PaymentSuccessConsumer ──消费──→ 更新 seats 表        │
│                                                                             │
│  TPP_ORDER_TOPIC (订单 Topic)                                               │
│  │                                                                          │
│  │  ┌─ OrderService.cancelOrder() ──发送──→ CANCEL_ORDER 标签               │
│  │  │                                    │                                 │
│  │  │  ┌─ OrderService.createPendingOrder() ──延时发送──→ 15分钟后          │
│  │  │                                                           │         │
│  │  └────────────────────────────────────────────────────────┼─────────────┤
│  │                                                            ▼             │
│  └── SeckillService.OrderCancelConsumer ──消费──→ 释放 Redis 座位           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、删除的代码

接入 RocketMQ 后，以下代码已被删除：

| 文件 | 删除内容 | 原因 |
|------|----------|------|
| `OrderService.java` | `cancelTimeoutOrders()` 方法 | 改用延时消息 |
| `OrderServiceImpl.java` | `cancelTimeoutOrders()` 实现 | 改用延时消息 |

---

## 七、幂等性保证

### 7.1 支付成功消息幂等

```java
// PaymentSuccessConsumer.java
@Override
public void onMessage(PaymentSuccessMessage message) {
    // ✅ 消费前检查是否已处理
    if (sessionService.isSeatsMarkedSold(message.getOrderNo())) {
        log.info("座位已标记为 sold，跳过处理");
        return;  // 幂等：重复消息直接跳过
    }

    // 标记座位为已售出
    sessionService.markSeatsSold(message);
}
```

### 7.2 取消消息幂等

```java
// OrderCancelConsumer.java
@Override
public void onMessage(OrderCancelMessage message) {
    // 调用 SeckillService 释放座位
    // Redis 操作本身幂等（unlockSeats 不会报错）
    seckillService.releaseSeats(...);
}
```

---

## 八、运行前检查

### 8.1 启动 RocketMQ

```bash
# 1. 启动 NameServer
sh mqnamesrv

# 2. 启动 Broker（允许自动创建 Topic，仅开发环境）
sh mqbroker -n localhost:9876 -c /path/to/broker.conf

# 或者直接启动（默认配置）
sh mqbroker -n localhost:9876
```

### 8.2 创建 Topic（重要！）

**首次运行前必须手动创建 Topic**，否则会报错 `No route info of this topic`：

```bash
# 方式一：使用 mqadmin 命令创建（推荐）
mqadmin updateTopic -n localhost:9876 -t TPP_PAYMENT_TOPIC -c DefaultCluster
mqadmin updateTopic -n localhost:9876 -t TPP_ORDER_TOPIC -c DefaultCluster

# 方式二：配置 Broker 允许自动创建（仅开发环境）
# 在 broker.conf 中添加：
# autoCreateTopicEnable=true
```

### 8.3 验证 Topic 是否创建成功

```bash
# 查询所有 Topic
mqadmin topicList -n localhost:9876

# 查看 Topic 详情
mqadmin topicStatus -n localhost:9876 -t TPP_PAYMENT_TOPIC
mqadmin topicStatus -n localhost:9876 -t TPP_ORDER_TOPIC
```

---

## 九、总结

| 方面 | 接入前 | 接入后 |
|------|--------|--------|
| **支付流程** | 同步调用 SessionService | 异步 MQ 消息 |
| **超时取消** | 定时任务轮询 | 延时消息精准触发 |
| **服务耦合** | OrderService 直接依赖 SessionService | 通过 MQ 解耦 |
| **实时性** | 受同步调用影响 | 异步处理，响应更快 |
| **可靠性** | 单点故障风险 | 消息持久化，可重试 |

---

**文档版本**: v1.0
**更新日期**: 2026-03-24
**作者**: duanyan
