# OpenResty 阅读顺序

目标：只建立阅读路径，不在这里写分析结论。

## 阅读规则

1. 按顺序读，不跳读。
2. 每一轮只关注当前阶段指定的函数或配置。
3. 读完一轮再记笔记，不边读边发散。
4. 笔记只记 4 类信息：阶段、入口、状态、返回点。

## 阅读顺序

### 1. 先看入口路由

文件：

- `/usr/local/openresty/nginx/conf/app.conf`

只看：

- `/internal/seckill/gate/status`
- `/internal/seckill/gate/config`
- `/internal/seckill/gate/reset`
- `/api/seckill/lock`

输出：

- 画出请求生命周期：`access_by_lua -> proxy_pass -> header_filter_by_lua -> body_filter_by_lua -> log_by_lua`

### 2. 再看工具函数

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate_util.lua`

只看：

- `now_ms`
- `ensure_request_id`
- `read_json_body`
- `build_seat_fingerprint`
- `respond`
- `log_decision`

### 3. 再看状态模型

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只看：

- `DEFAULT_GATE_CONFIG`
- `SESSION_GATE_CONFIG`
- `metric_key`
- `terminal_key`
- `unavailable_key`
- `override_key`
- `tokens_key`
- `tokens_ts_key`
- `inflight_key`

输出：

- 把所有 shared dict key 分类写下来

### 4. 看配置与状态查询

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只看：

- `get_gate_config`
- `get_metric_snapshot`
- `sanitize_config_update`
- `write_json`
- `reset_session_runtime`
- `reset_session_metrics`

### 5. 看并发控制基础函数

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只看：

- `current_tokens`
- `try_acquire_token`
- `get_inflight`
- `get_tokens`
- `mark_terminal_hold`
- `mark_unavailable_hold`

### 6. 第一轮精读主流程前半段

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只记录：

- header 校验
- body 校验
- sessionId 校验
- seatIds 校验
- terminal 拦截
- unavailable 拦截
- dedupe 拦截

### 7. 第二轮精读主流程后半段

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只记录：

- 等待循环
- inflight 增减
- token 获取
- 超时拒绝

### 8. 看上游结果处理

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只看：

- `process_upstream_payload`
- `capture_upstream_result`

输出：

- 整理“哪些上游业务结果会反写本地状态”

### 9. 看收尾与状态接口

文件：

- `/usr/local/openresty/nginx/lua/seckill_gate.lua`

只看：

- `release_inflight`
- `status`
- `update_config`
- `reset_session`

### 10. 最后做一张表

自己整理 4 列：

- 阶段
- 读的函数或配置
- 修改了什么状态
- 提前返回或结束条件

## 最后一轮重读

只盯这些结果分支：

- `reject_invalid`
- `reject_terminal`
- `reject_recent_unavailable`
- `reject_dedupe`
- `reject_ratelimit`
- `release_inflight`
