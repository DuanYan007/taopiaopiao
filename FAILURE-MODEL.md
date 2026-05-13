# 业务高可用故障模型与推进清单

目标：围绕当前真实链路 `OpenResty -> gateway -> seckill-service -> order-service -> payment-system / MQ / Redis` 做故障建模、可观测性和幂等验证。

## 推进原则

1. 先做故障建模，再补可观测性，再做故障演练。
2. 先处理会卡主链路、且当前不可见的问题。
3. 每次只推进一个故障点，做到可注入、可观测、可恢复、可复盘。

## 故障清单

| 编号 | 故障点 | 影响 | 当前机制 | 主要缺口 | 下一步 | 验证方式 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F1 | Redis 临时不可用 | 锁座失败，热路径受阻 | 锁座直接失败 | 缺少明确降级语义与健康暴露 | 定义 Redis 故障时的业务表现与内部状态输出 | 人工停 Redis，观察锁座与恢复表现 | 待推进 |
| F2 | Try 建单成功后 `TIMEOUT_CHECK` 延时消息发送失败 | 订单已落库但超时裁决缺失 | `order-service` 本地事务内发延时消息 | 缺少发送失败、重试和补偿告警 | 增加发送结果统计与补偿处理 | 人工停 MQ 或注入发送异常 | 待推进 |
| F3 | `ORDER_PAID` 重复投递 | 重复确认售出 | 订单服务已有状态幂等保护 | 仍需重复消费证明与告警 | 做重复消费测试并核对唯一性约束 | 重放同一消息，多次消费校验最终态 | 待推进 |
| F4 | `TIMEOUT_CHECK` 与支付成功并发竞争 | 订单状态可能冲突 | `markPaidIfUnpaid` / `markTimeoutIfUnpaid` 条件更新 + Redis 侧幂等确认/释放 | 仍缺少常态化自动化脚本接入 | 保持“只裁决 `UNPAID`”语义，并把当前回放手段收敛进测试流程 | 并发触发支付/超时，校验最终态 | 已验证 |
| F5 | 用户取消与支付成功并发竞争 | 取消后又支付成功，或支付后误取消 | `markCancelledIfUnpaid` / `markPaidIfUnpaid` 条件更新 + Redis 侧幂等确认/释放 | 仍缺少常态化自动化脚本接入 | 保持“终态不互相覆盖”语义，并把当前回放手段收敛进测试流程 | 并发触发取消/支付，校验最终态 | 已验证 |
| F6 | `confirmOrder` / `cancelOrder` 回打 `seckill-service` 失败 | 座位确认或释放延迟 | 业务调用可重试，Redis 以 `orderNo` 为 owner | 缺少回打失败的积压观测 | 增加 RPC 失败与重试统计 | 人工停 seckill-service，观察重试恢复 | 待推进 |
| F7 | OpenResty 上游超时或后端不可用 | 入口失败，可能产生并发计数泄漏或错误放行 | 已有 inflight 释放逻辑 | 超时/5xx 分类不够清晰 | 强化入口故障分类与 inflight 回收验证 | 人工停后端或制造超时，观察 gate 状态 | 待推进 |

## 推荐推进顺序

### 第一阶段：入口与基础依赖

- F7 OpenResty 上游超时或后端不可用
- F1 Redis 临时不可用

### 第二阶段：消息与超时裁决

- F2 Try 建单后 `TIMEOUT_CHECK` 延时消息发送失败
- F3 `ORDER_PAID` 重复投递
- F4 `TIMEOUT_CHECK` 与支付成功并发竞争

### 第三阶段：取消与回打幂等

- F5 用户取消与支付成功并发竞争
- F6 `confirmOrder` / `cancelOrder` 回打 `seckill-service` 失败

## 当前已验证结论

### F4 `TIMEOUT_CHECK` 与支付成功并发竞争

- 当前裁决语义已经固定为“只处理仍为 `UNPAID` 的订单”。
- `OrderPaidConsumer` 只允许 `UNPAID -> PAID`。
- `OrderTimeoutCheckConsumer` 只允许 `UNPAID -> PAID` 或 `UNPAID -> TIMEOUT`。
- 已通过真实链路回放验证：在超时消费者被测试钩子短暂延迟期间，支付成功先落库后，超时消费恢复时会识别订单已是 `PAID` 并直接跳过。
- 运行时延迟钩子默认关闭，仅在显式开启 `tpp.test.runtime-hooks-enabled=true` 时用于测试。

### F5 用户取消与支付成功并发竞争

- 当前裁决语义已经固定为“终态不互相覆盖”。
- 用户取消只允许 `UNPAID -> CANCELLED`。
- `ORDER_PAID` 迟到时，如果订单已是 `CANCELLED / TIMEOUT / REFUNDED`，消费者只记录告警并直接返回，不再抛异常重试。
- 已通过真实链路回放验证：
  - 取消与支付成功并发时，最终只会留下一个终态。
  - 对已取消订单手工补发迟到支付成功消息时，订单服务会跳过该消息，不会把订单改回 `PAID`。

## 单项推进模板

每推进一个故障点，只回答以下 5 个问题：

1. 如何注入故障？
2. 期望系统表现是什么？
3. 需要补哪些可观测性？
4. 如何验证恢复完成？
5. 这项能力最后如何写进简历？

## F7 细化设计

- `/api/seckill/lock` 在 OpenResty 中先执行 `access_by_lua`，放行后再反向代理到 gateway。
- inflight 释放走双保险：`header_filter_by_lua` 和 `log_by_lua` 都会调用释放逻辑，并且靠 `ngx.ctx.seckill_gate_inflight_acquired` 防止重复释放。
- 当前更大的问题不是“完全没有释放”，而是“故障发生后看不清到底发生了什么”。

### 现有观测的关键问题

- `/internal/seckill/gate/status` 目前只暴露放行/拒绝计数，以及少量上游结果计数。
- `capture_upstream_result()` 依赖响应体 JSON 解析；如果 gateway 连接失败、返回 502/504、超时、空响应、非 JSON，当前基本无法准确分类。
- 现有状态接口容易把“业务失败”和“基础设施失败”混在一起。

### F7 第一轮目标

1. 保持现有 inflight 释放模型不变，先不改并发控制主逻辑。
2. 把“业务结果分类”和“基础设施故障分类”明确拆开。
3. 让 `/internal/seckill/gate/status` 能直接看出：是 gateway 挂了、超时了、返回 5xx，还是只是业务拒绝。

### 建议新增的观测维度

- `infra_upstream_connect_fail`
- `infra_upstream_timeout`
- `infra_upstream_5xx`
- `infra_upstream_non_json`
- `infra_upstream_empty_body`

同时保留现有业务计数，但建议逐步把名字改清楚：

- `upstream_unavailable` -> 更准确地表达为“seat unavailable”语义
- `upstream_other` -> 只保留给“未知业务返回”，不要混入基础设施故障

### 最小推进方案

1. 在 `body_filter` 继续处理业务 JSON 分类。
2. 在 `log_by_lua` 或等价阶段补充基于 HTTP 状态和上游状态的基础设施分类。
3. 在 `status` 接口中同时输出业务计数与基础设施计数。
4. 明确 F7 的验收标准：入口失败后 inflight 必须回到 0，且故障类型可被区分。

### 建议先做的两种演练

1. 停掉 gateway，验证连接失败场景。
   期望：请求快速失败，inflight 回收，基础设施失败计数增加。
2. 制造上游慢响应，验证超时场景。
   期望：请求按预期超时，inflight 回收，不误记为业务失败。

### 这一项做完后的价值

- 能证明入口层不仅有限流，而且具备故障识别与快速恢复能力。
- 能把“业务不可用”和“系统不可用”分开观测，后续才能继续推进 Redis、MQ、补偿链路的高可用治理。
