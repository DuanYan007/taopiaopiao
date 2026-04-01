# 秒杀购票接口文档

## 业务流程

```
选座页面 → 锁座接口 → 支付确认页 → 支付系统 → 支付结果页
```

**重要变更**：
- 锁座接口现在会调用支付系统创建支付订单，并返回支付页面URL（payUrl）
- 前端应重定向到支付系统页面完成支付，而非调用原有支付接口
- 支付完成后通过RocketMQ消息异步更新订单状态

---

## 1. 获取座位布局（含状态和价格）

**接口**: `GET /api/seckill/{sessionId}/layout`

**用途**: 选座页面初始化，一次性获取座位布局、实时状态和价格

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | Long | 场次ID |

**请求头**:
```
X-User-Id: {userId}
Authorization: Bearer {token}  // 可选
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "sessionId": 1,
    "meta": {
      "totalSeats": 160,
      "totalAreas": 2,
      "areaNames": ["VIP区", "A区"],
      "areaPrices": [2560, 1280]
    },
    "areas": [
      [
        {"id": 1, "row": 1, "col": 1, "status": 0},
        {"id": 2, "row": 1, "col": 2, "status": 0}
      ],
      [
        {"id": 81, "row": 1, "col": 1, "status": 2},
        {"id": 82, "row": 1, "col": 2, "status": 0}
      ]
    ]
  }
}
```

**字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | Long | 场次ID |
| meta.totalSeats | Integer | 总座位数 |
| meta.totalAreas | Integer | 区域数量 |
| meta.areaNames | String[] | 区域名称数组 |
| meta.areaPrices | Integer[] | 各区域价格数组 |
| areas | 二维数组 | 各区域座位列表，areas[i]对应areaNames[i] |
| areas[i][j].id | Long | 座位ID（数据库主键） |
| areas[i][j].row | Integer | 行号 |
| areas[i][j].col | Integer | 列号 |
| areas[i][j].status | Integer | 状态：0=空闲, 1=锁定, 2=售出 |

**错误响应**:
```json
{
  "code": 404,
  "msg": "场次不存在或未初始化"
}
```

---

## 2. 锁定座位（创建订单）

**接口**: `POST /api/seckill/lock`

**用途**: 用户选座后锁定座位，同时创建待支付订单

**请求头**:
```
Content-Type: application/json
X-User-Id: {userId}
Authorization: Bearer {token}  // 可选
```

**请求体**:
```json
{
  "sessionId": 1,
  "userId": 1,
  "seatIds": [1, 2, 3],
  "expireSeconds": 900,
  "unitPrice": 2560
}
```

**请求参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | Long | 是 | 场次ID |
| userId | Long | 是 | 用户ID |
| seatIds | Long[] | 是 | 座位ID数组 |
| expireSeconds | Integer | 是 | 锁定时长(秒)，默认900 |
| unitPrice | Integer | 否 | 座位单价（前端传，后端需校验） |

**成功响应**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "success": true,
    "code": 0,
    "message": "锁座成功",
    "lockId": "lock_123456",
    "lockedSeats": [1, 2, 3],
    "orderNo": "ORD202506011930001",
    "payUrl": "http://localhost:7500/payment/pay?paymentNo=PAY202506011930001"
  }
}
```

**响应字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 是否成功 |
| code | Integer | 业务状态码，0表示成功 |
| message | String | 消息 |
| lockId | String | 锁定ID |
| lockedSeats | Array | 锁定的座位ID列表 |
| orderNo | String | **订单号** |
| payUrl | String | **支付页面地址（支付系统返回，前端需重定向至此）** |

**错误响应**:
```json
{
  "code": 400,
  "msg": "座位已被锁定或售出"
}
```

---

## 3. 支付订单（已废弃）

> **注意**: 此接口已废弃，支付流程现已集成到锁座接口中。锁座成功后会直接返回支付系统URL（payUrl），前端应重定向到该URL完成支付。

**原接口**: `POST /api/client/orders`

**新支付流程**:
1. 锁座接口返回 `payUrl` 字段
2. 前端重定向到 `payUrl`（支付系统页面）
3. 用户在支付系统完成支付
4. 支付系统通过回调通知订单服务
5. RocketMQ消息队列异步更新订单状态

---

## 4. 释放座位（可选）

**接口**: `POST /api/seckill/release`

**用途**: 用户取消选座时释放锁定的座位

**请求头**:
```
Content-Type: application/json
X-User-Id: {userId}
```

**请求体**:
```json
{
  "sessionId": 1,
  "userId": 1,
  "seatIds": [1, 2, 3]
}
```

**成功响应**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "success": true,
    "releasedSeats": [1, 2, 3]
  }
}
```

---

## 5. 获取订单详情（支付后）

**接口**: `GET /api/client/orders/{orderNo}`

**用途**: 支付成功后获取订单详情，展示电子票

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| orderNo | String | 订单号 |

**请求头**:
```
X-User-Id: {userId}
Authorization: Bearer {token}  // 可选
```

**成功响应**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "orderNo": "ORD202506011930001",
    "eventId": 10,
    "sessionId": 1,
    "eventName": "演唱会名称",
    "venueName": "体育馆",
    "startTime": "2025-06-01 19:30:00",
    "seatCount": 3,
    "seatInfo": "VIP区 1排01座、1排02座、1排03座",
    "totalAmount": 7680,
    "status": 2,
    "statusDesc": "已支付",
    "payTime": "2025-06-01 19:35:20",
    "createdAt": "2025-06-01 19:30:00"
  }
}
```

---

## 订单状态枚举

| 状态值 | 说明 | 前端显示 |
|--------|------|----------|
| 1 | 未支付 | "未支付" |
| 2 | 已支付 | "已支付" |
| 3 | 已取消 | "已取消" |
| 4 | 已退款 | "已退款" |

---

## 前端 sessionStorage 存储字段

| 字段 | 说明 | 存储时机 |
|------|------|----------|
| sessionId | 场次ID | 锁座成功后 |
| eventId | 演出ID | 锁座成功后 |
| selectedSeats | 选中的座位信息（JSON） | 锁座成功后 |
| totalPrice | 订单总金额 | 锁座成功后 |
| lockId | 锁定ID | 锁座成功后 |
| lockExpireTime | 锁定过期时间 | 锁座成功后 |
| orderNo | 订单号 | 锁座成功后 |
| sessionData | 场次详情（JSON） | 锁座成功后 |

---

## 完整业务流程图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         选座页面                                    │
│  GET /api/seckill/{sessionId}/layout                               │
│  → 获取座位布局、状态、价格                                         │
│  → 用户选择座位                                                    │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         锁座接口                                    │
│  POST /api/seckill/lock                                            │
│  → 锁定Redis座位 + 创建待支付订单(RocketMQ事务消息)                 │
│  → 调用支付系统创建支付订单                                         │
│  → 返回 orderNo + payUrl                                           │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      支付确认页面                                    │
│  → 显示订单信息、倒计时                                             │
│  → 用户点击"确认支付" → 重定向到 payUrl                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    支付系统页面 (外部系统)                           │
│  http://localhost:7500/payment/pay?paymentNo=xxx                    │
│  → 用户完成支付操作                                                 │
│  → 支付系统回调订单服务                                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    RocketMQ 消息队列                                 │
│  → OrderCreatedConsumer 消费订单创建消息                             │
│  → 查询支付系统确认支付状态                                         │
│  → 更新订单状态、座位状态                                           │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      支付结果页面                                    │
│  GET /api/client/orders/{orderNo}                                  │
│  → 获取订单详情                                                    │
│  → 展示电子票                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

## 后端服务交互说明

1. **秒杀服务 (Seckill Service, 8083)**
   - 处理座位锁定（Redis）
   - 调用订单服务创建待支付订单（通过RocketMQ事务消息）
   - 调用支付系统创建支付订单
   - 返回支付URL给前端

2. **订单服务 (Order Service, 8087)**
   - 接收RocketMQ事务消息
   - 创建订单记录
   - 发送延迟消息（5分钟后取消未支付订单）
   - 通过回调接口查询支付系统状态

3. **支付系统 (Payment System, 7500)**
   - 外部独立系统
   - 创建支付订单并返回支付URL
   - 处理用户支付操作
   - 提供支付状态查询接口

4. **场次服务 (Session Service, 8084)**
   - 接收订单状态变更消息
   - 更新数据库座位状态

---

## 前端修改指南

### 变更概述

| 变更项 | 修改前 | 修改后 |
|--------|--------|--------|
| 支付方式 | 调用 `POST /api/client/orders` | 重定向到 `payUrl` |
| 锁座响应 | 无 `payUrl` 字段 | 新增 `payUrl` 字段 |
| 订单超时 | 15分钟 | 5分钟 |

### 详细修改步骤

#### 步骤1：修改锁座成功后的处理逻辑

**修改前**：
```javascript
// 锁座成功后
const response = await lockSeats(request);
if (response.success) {
  // 存储订单信息
  sessionStorage.setItem('orderNo', response.orderNo);
  // 跳转到支付确认页
  router.push('/payment-confirm');
}
```

**修改后**：
```javascript
// 锁座成功后
const response = await lockSeats(request);
if (response.success) {
  // 存储订单信息
  sessionStorage.setItem('orderNo', response.orderNo);
  sessionStorage.setItem('payUrl', response.payUrl);  // 新增
  // 跳转到支付确认页
  router.push('/payment-confirm');
}
```

#### 步骤2：修改支付确认页的"确认支付"按钮

**修改前**：
```javascript
const handlePay = async () => {
  const orderNo = sessionStorage.getItem('orderNo');
  // 调用后端支付接口
  await payOrder({ orderNo });
  // 跳转到支付结果页
  router.push('/payment-result');
};
```

**修改后**：
```javascript
const handlePay = () => {
  const payUrl = sessionStorage.getItem('payUrl');
  // 直接重定向到支付系统
  window.location.href = payUrl;
};
```

#### 步骤3：支付系统回调处理

支付完成后，支付系统会回调前端。需要新增一个回调页面：

```javascript
// /payment-callback 页面
const PaymentCallback = () => {
  const params = new URLSearchParams(window.location.search);
  const orderNo = params.get('orderNo');
  const status = params.get('status');  // success / fail / cancel

  useEffect(() => {
    if (status === 'success') {
      // 跳转到支付结果页
      router.push(`/payment-result?orderNo=${orderNo}&status=success`);
    } else if (status === 'fail') {
      router.push(`/payment-result?orderNo=${orderNo}&status=fail`);
    } else {
      // 用户取消，返回订单列表
      router.push('/orders');
    }
  }, [orderNo, status]);

  return <div>支付处理中...</div>;
};
```

#### 步骤4：更新倒计时显示

**修改前**：
```javascript
const COUNTDOWN = 15 * 60;  // 15分钟
```

**修改后**：
```javascript
const COUNTDOWN = 5 * 60;  // 5分钟
```

### API 响应变更对比

#### 锁座接口响应（新增字段）

```json
{
  "code": 200,
  "data": {
    "success": true,
    "orderNo": "ORD202506011930001",
    "payUrl": "http://localhost:7500/payment/pay?paymentNo=PAY202506011930001"  // 新增
  }
}
```

### 废弃接口

| 接口 | 状态 | 说明 |
|------|------|------|
| `POST /api/client/orders` | ❌ 已废弃 | 原支付接口，不再使用 |

### 测试流程

1. 调用锁座接口，确认返回 `payUrl`
2. 点击"确认支付"，确认跳转到支付系统页面
3. 在支付系统完成支付（或模拟支付）
4. 确认回调到前端支付结果页
5. 调用订单详情接口，确认订单状态已更新

### 注意事项

1. **payUrl 可能为 null**：如果支付系统调用失败，`payUrl` 可能为空，需要处理这种情况
2. **5分钟倒计时**：订单超时时间改为 5 分钟，前端倒计时需同步调整
3. **支付系统地址**：开发环境为 `http://localhost:7500`，生产环境需配置正确的支付系统地址
4. **回调地址配置**：需要在支付系统中配置前端回调地址
