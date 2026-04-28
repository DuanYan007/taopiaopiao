# 淘票票支付系统

## 简介

独立支付系统，模拟支付宝/微信支付功能，用于淘票票项目的支付流程学习。

**设计原则**：支付系统只负责**存储和返回支付状态**，不主动通知业务系统。业务系统通过 RocketMQ 回查机制主动查询支付状态。

## 技术栈

- Spring Boot 3.2.4
- Knife4j 4.3.0
- 内存存储（`ConcurrentHashMap`）

## 端口与配置

- **服务端口**: 7500
- **运行方式**: 内存模式，无独立数据库
- **API 文档**: http://192.168.3.36:7500/doc.html

## 启动服务

```bash
mvn spring-boot:run
```

## API 接口

### 1. 创建支付订单

```bash
POST http://192.168.3.36:7500/payment/create
Content-Type: application/json

{
  "orderNo": "ORDER20240329001",
  "amount": 128.00,
  "payMethod": "MOCK",
  "body": "演唱会门票"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderNo": "ORDER20240329001",
    "paymentNo": "PAY202403291234567890",
    "amount": "128.00",
    "payUrl": "/payment/simulate/success?orderNo=ORDER20240329001",
    "qrCode": "mock_qr_code_PAY202403291234567890"
  }
}
```

### 2. 查询支付状态

```bash
GET http://192.168.3.36:7500/payment/query?orderNo=ORDER20240329001
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderNo": "ORDER20240329001",
    "paymentNo": "PAY202403291234567890",
    "status": "PENDING",
    "statusDesc": "待支付",
    "amount": 128.00,
    "transactionId": null,
    "payMethod": "MOCK",
    "createdAt": "2024-03-29T12:34:56",
    "paidAt": null
  }
}
```

### 3. 模拟支付成功（测试用）

```bash
GET http://192.168.3.36:7500/payment/simulate/success?orderNo=ORDER20240329001
```

### 4. 模拟支付失败（测试用）

```bash
POST http://192.168.3.36:7500/payment/simulate/fail
Content-Type: application/json

{
  "orderNo": "ORDER20240329001"
}
```

## 支付状态

| 状态 | 说明 |
|------|------|
| PENDING | 待支付 |
| SUCCESS | 支付成功 |
| FAILED | 支付失败 |
| CANCELLED | 已取消 |

## 存储方式

支付记录仅保存在进程内存中，重启后清空。

## 与业务系统的交互

```
业务系统 (订单服务)           支付系统
     │                            │
     ├── POST /create ───────────>│  创建支付订单
     │<─── payment_no / payUrl ──┤
     │                            │
     │                            │  用户完成支付
     │                            │  (模拟接口)
     │                            │
     ├── GET /query ─────────────>│  查询支付状态
     │<─── status (SUCCESS) ──────┤
     │                            │
     │  根据状态更新订单            │
```

## 设计原则

1. **被动服务**：支付系统只接收请求并返回数据，不主动推送
2. **状态存储**：支付系统是支付状态的唯一真实来源
3. **查询驱动**：业务系统主动查询支付状态

## 注意事项

1. 本系统为学习项目，使用内存存储辅助数据库查询
2. 支付系统不提供回调通知，业务系统通过 RocketMQ 回查机制查询状态
3. 生产环境请使用真实的支付宝/微信支付 SDK
