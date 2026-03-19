# 架构决策 - 淘票票后端项目

记录已确认的技术选型与架构决策。

---

## 秒杀/抢票架构

### 决策时间
2026-03-19

### 决策内容
使用 Redis + Lua 脚本实现原子性锁座机制，防止超卖。

### 核心组件
| 组件 | 说明 |
|------|------|
| Redis | 存储座位实时状态 |
| Lua 脚本 | 保证锁座操作的原子性 |
| Redisson | Redis 客户端 |

### Redis 数据结构
```
# 座位状态（String，值=状态码）
seat:{sessionId}:{seatId} → 0/1/2

# 用户锁座记录（Hash，field=seatId, value=1）
user:{userId}:locks

# 场次座位集合（Set，存储所有座位ID）
session:{sessionId}:seats
```

### 座位状态码（SeatStatus）
- **0** - AVAILABLE（可选）
- **1** - LOCKED（已锁定，有 TTL）
- **2** - SOLD（已售出）

### Lua 脚本
1. **lock_seat.lua** - 锁座脚本
   - 返回 0: 成功
   - 返回 1: 座位不存在
   - 返回 2: 座位已锁定或已售出
   - 返回 3: 用户已锁定该座位（重复购票）

2. **unlock_seat.lua** - 释放座位脚本
   - 返回释放的座位数量

3. **confirm_purchase.lua** - 确认购买脚本
   - 返回 0: 成功
   - 返回 1: 无权操作

---

---

## 微服务架构

### 决策时间
2026-01-27（项目启动）

### 决策内容
采用微服务架构，按业务领域拆分服务。

### 服务拆分
| 服务 | 职责 |
|------|------|
| user-service | 用户管理、认证授权 |
| venue-service | 场馆信息管理 |
| event-service | 演出信息管理 |
| session-service | 场次信息管理 |

### 模块分层
每个服务包含三个子模块：
- **api**：DTO、接口定义
- **application**：Controller、Service、Mapper
- **domain**：实体类

### 公共模块
- **taopiaopiao-common**：无 Web 依赖的核心模块
- **taopiaopiao-common-web**：包含 Web 相关配置

---

## ORM 框架：MyBatis-Plus

### 决策时间
2026-01-27（项目启动）

### 决策内容
使用 MyBatis-Plus 作为 ORM 框架。

### 版本
3.5.7（Spring Boot 3.x 兼容版本）

### 关键配置
- 分页插件：`PaginationInnerInterceptor`
- 主键类型：`@TableId(type = IdType.AUTO)`
- 逻辑删除：deleted 字段（0=未删除，1=已删除）
- 自动填充：`@TableField(fill = FieldFill.INSERT)` (createdAt), `@TableField(fill = FieldFill.INSERT_UPDATE)` (updatedAt)

---

## 数据库：MySQL 8.4.8

### 决策时间
2026-01-27（项目启动）

### 决策内容
使用 MySQL Server 8.4.8 作为主数据库。

### 连接配置
- **端口**: 3306
- **地址**: localhost
- **数据库**: taopiaopiao
- **用户**: root
- **密码**: root
- **时区**: Asia/Shanghai

### JDBC 驱动
mysql-connector-j 9.1.0（兼容 MySQL 8.4.8）

### URL 格式
```
jdbc:mysql://localhost:3306/taopiaopiao?serverTimezone=Asia/Shanghai
```

---

## API 文档：Knife4j

### 决策时间
2026-01-27（项目启动）

### 决策内容
使用 Knife4j 作为 API 文档工具。

### 版本
4.5.0（OpenAPI 3.0 兼容）

### 访问地址
- **开发环境**: http://localhost:8080/doc.html
- **生产环境**: production: true

---

## 统一响应格式：Result<T>

### 决策时间
2026-01-27（项目启动）

### 决策内容
所有 API 统一使用 `Result<T>` 格式返回。

### 响应结构
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {},
  "timestamp": 1234567890
}
```

### 状态码
- **200**: 成功
- **400**: 客户端错误
- **401**: 未认证
- **403**: 无权限
- **404**: 资源不存在
- **500**: 服务器内部错误

### 全局异常处理
- `GlobalExceptionHandler` 统一捕获异常
- `BusinessException` 业务异常（400状态码）
- 其他异常（500状态码）

---

## OpenFeign 服务间调用

### 决策时间
2026-02-12（集成 OpenFeign）

### 决策内容
使用 OpenFeign 实现微服务间 HTTP 调用。

### 版本
- OpenFeign: 4.1.0
- Loadbalancer: 4.1.0

### 调用方式
1. **Client 接口返回类型**：与服务实际返回格式一致，声明为 `Result<T>`
2. **Service 层处理**：通过 `resp.getData()` 获取实体对象
3. **不使用自定义 Decoder**：使用 Feign 默认 Decoder 即可正确处理

### 示例
```java
@FeignClient(name = "venue-service", path = "/admin/venues")
public interface VenueClient {
    @GetMapping("/{id}")
    Result<VenueResponse> getVenueById(@PathVariable("id") Long id);
}

// Service 中使用
Result<VenueResponse> resp = venueClient.getVenueById(id);
VenueResponse venue = resp.getData();
```

---

## 服务注册与发现：Nacos

### 决策时间
2026-01-27（项目启动）

### 决策内容
使用 Nacos 作为服务注册与配置中心。

### 功能
- 服务注册与发现
- 配置管理（命名空间、分组、Data ID）

### 配置
- **Nacos Server**: localhost:8848/nacos
- **命名空间**: dev
- **用户名**: nacos
- **密码**: nacos
- **端口**: 8848

---

## 服务端口配置

### 决策时间
2026-03-19

### 端口分配
| 服务 | 端口 | 说明 |
|------|------|------|
| gateway | 8080 | 网关服务 |
| user-service | 8081 | 用户服务 |
| venue-service | 8082 | 场馆服务 |
| event-service | 8083 | 演出服务 |
| session-service | 8084 | 场次服务 |
| seat-template-service | 8085 | 座位模板服务 |
| seckill-service | 8086 | 秒杀/选座服务 |
| order-service | 8087 | 订单服务 |

---

## 命名规范

### 包名
- `com.duanyan.taopiaopiao`
- 小写开头，驼峰命名

### 类名
- **Controller**: `*Controller`
- **Service**: `*Service` / `*ServiceImpl`
- **Service 接口**: `IService`
- **Mapper**: `*Mapper`
- **Entity/DO**: `*` (PascalCase)
- **DTO**: `*Request` / `*Response`

### 方法名
- **查询**: `get*`, `list*`, `query*`
- **新增**: `save*`, `create*`, `insert*`, `add*`
- **更新**: `update*`, `modify*`
- **删除**: `remove*`, `delete*`

### 常量名
- **全大写+下划线**: `CONSTANT_NAME`

---

## MyBatis-Plus 自动填充

### 决策时间
2026-02-11（集成自动填充功能）

### 决策内容
使用 MyBatis-Plus 的 `MetaObjectHandler` 实现时间戳自动填充。

### 填充规则
- **createdAt**：插入时填充
- **updatedAt**：插入和更新时填充

### 实现方式
```java
@Component
public class MyBatisPlusMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 注意事项
- 更新时如果字段已有值（从数据库查询出的对象），自动填充不会生效
- 需要在更新前手动设置 `entity.setUpdatedAt(null)`

---

## 订单服务架构

### 决策时间
2026-03-19

### 决策内容
订单服务采用「锁座 → 待支付订单 → 支付 → 确认购买」的流程。

### 订单状态（OrderStatus）
| 状态码 | 枚举值 | 说明 |
|--------|--------|------|
| 1 | UNPAID | 未支付（15分钟过期） |
| 2 | PAID | 已支付 |
| 3 | CANCELLED | 已取消（用户主动取消） |
| 4 | REFUNDED | 已退款 |
| 5 | TIMEOUT | 超时取消（系统自动取消） |

### 核心流程

#### 1. 锁座流程（SeckillService）
```
用户请求锁座
  ↓
Redis Lua 原子性锁座
  ↓ 成功
插入 seat_locks 记录（status=LOCKED, orderNo=null）
  ↓
调用订单服务创建待支付订单
  ↓ 成功
更新 seat_locks 的 orderNo
  ↓
返回 orderNo 给前端
```

#### 2. 支付流程（OrderService）
```
用户提交支付（携带 orderNo）
  ↓
验证订单状态（必须是 UNPAID 且未过期）
  ↓
调用秒杀服务标记座位已支付（Redis 状态 1→2）
  ↓
更新 seat_locks 状态为 PAID
  ↓
调用场次服务更新 seats 表状态为 sold
  ↓
更新订单状态为 PAID
  ↓
支付成功
```

#### 3. 取消订单流程
```
用户取消或超时
  ↓
调用秒杀服务释放座位（Redis 删除用户锁座记录）
  ↓
更新 seat_locks 状态为 RELEASED
  ↓
更新订单状态为 CANCELLED/TIMEOUT
```

---

## 座位锁定记录表（seat_locks）

### 决策时间
2026-03-19

### 决策内容
使用 seat_locks 表记录所有座位锁定历史，用于追溯和对账。

### 表结构
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| session_id | BIGINT | 场次ID |
| user_id | BIGINT | 用户ID |
| seat_id | VARCHAR | 座位ID |
| seat_row | INT | 行号 |
| seat_col | INT | 列号 |
| lock_time | BIGINT | 锁定时间戳 |
| expire_time | BIGINT | 过期时间戳 |
| status | INT | 状态（0=已释放，1=已锁定，2=已支付） |
| order_no | VARCHAR | 关联订单号 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 锁定状态（LockStatus）
- **0** - RELEASED（已释放）
- **1** - LOCKED（已锁定）
- **2** - PAID（已支付）

### 与 Redis 的关系
- Redis：存储座位实时状态（快速读写）
- seat_locks：记录锁定历史（持久化、追溯）

---

### 订单号生成
- 使用简化版雪花算法（OrderIdGenerator）
- 仅包含：时间戳 + 序列号（无机器ID和数据中心ID）
- START_TIMESTAMP = 1735660800000L（2025-01-01）

---
