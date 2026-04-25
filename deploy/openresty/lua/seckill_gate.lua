local cjson = require("cjson.safe")
local util = require("seckill_gate_util")

local _M = {}
local dict = ngx.shared.seckill_gate

local DEFAULT_GATE_CONFIG = {
    token_rate = 100,
    bucket_capacity = 150,
    max_inflight = 40,
    queue_timeout_ms = 80,
    user_cooldown_ms = 300,
    wait_step_ms = 10,
    unavailable_ttl_ms = 1500,
    success_hold_ttl_ms = 60000,
    duplicate_hold_ttl_ms = 30000
}

local SESSION_GATE_CONFIG = {
    [1] = {
        token_rate = 100,
        bucket_capacity = 150,
        max_inflight = 40,
        queue_timeout_ms = 80
    }
}

math.randomseed(util.now_ms() + ngx.worker.pid())

local function metric_key(session_id, name)
    return string.format("metric:%s:%s", tostring(session_id), name)
end

local function terminal_key(session_id, user_id, seat_fingerprint)
    return string.format("terminal:%s:%s:%s", tostring(session_id), tostring(user_id), seat_fingerprint)
end

local function unavailable_key(session_id, seat_fingerprint)
    return string.format("unavailable:%s:%s", tostring(session_id), seat_fingerprint)
end

local function override_key(session_id)
    return string.format("config_override:%s", tostring(session_id))
end

local function tokens_key(session_id)
    return "tokens:session:" .. session_id
end

local function tokens_ts_key(session_id)
    return "tokens_ts:session:" .. session_id
end

local function inflight_key(session_id)
    return "inflight:session:" .. session_id
end

local function get_gate_config(session_id)
    local stored_override = dict:get(override_key(session_id))
    local override = {}
    local static_override = SESSION_GATE_CONFIG[session_id] or {}
    for key, value in pairs(static_override) do
        override[key] = value
    end
    if stored_override then
        local decoded = cjson.decode(stored_override)
        if type(decoded) == "table" then
            for key, value in pairs(decoded) do
                override[key] = value
            end
        end
    end
    return {
        token_rate = override.token_rate or DEFAULT_GATE_CONFIG.token_rate,
        bucket_capacity = override.bucket_capacity or DEFAULT_GATE_CONFIG.bucket_capacity,
        max_inflight = override.max_inflight or DEFAULT_GATE_CONFIG.max_inflight,
        queue_timeout_ms = override.queue_timeout_ms or DEFAULT_GATE_CONFIG.queue_timeout_ms,
        user_cooldown_ms = override.user_cooldown_ms or DEFAULT_GATE_CONFIG.user_cooldown_ms,
        wait_step_ms = override.wait_step_ms or DEFAULT_GATE_CONFIG.wait_step_ms,
        unavailable_ttl_ms = override.unavailable_ttl_ms or DEFAULT_GATE_CONFIG.unavailable_ttl_ms,
        success_hold_ttl_ms = override.success_hold_ttl_ms or DEFAULT_GATE_CONFIG.success_hold_ttl_ms,
        duplicate_hold_ttl_ms = override.duplicate_hold_ttl_ms or DEFAULT_GATE_CONFIG.duplicate_hold_ttl_ms
    }
end

local function get_metric_snapshot(session_id)
    return {
        allow = dict:get(metric_key(session_id, "allow")) or 0,
        reject_dedupe = dict:get(metric_key(session_id, "reject_dedupe")) or 0,
        reject_ratelimit = dict:get(metric_key(session_id, "reject_ratelimit")) or 0,
        reject_terminal = dict:get(metric_key(session_id, "reject_terminal")) or 0,
        reject_recent_unavailable = dict:get(metric_key(session_id, "reject_recent_unavailable")) or 0,
        reject_invalid = dict:get(metric_key(session_id, "reject_invalid")) or 0,
        upstream_success = dict:get(metric_key(session_id, "upstream_success")) or 0,
        upstream_duplicate = dict:get(metric_key(session_id, "upstream_duplicate")) or 0,
        upstream_unavailable = dict:get(metric_key(session_id, "upstream_unavailable")) or 0,
        upstream_missing_seat = dict:get(metric_key(session_id, "upstream_missing_seat")) or 0,
        upstream_other = dict:get(metric_key(session_id, "upstream_other")) or 0
    }
end

local function sanitize_config_update(payload)
    local allowed_fields = {
        "token_rate",
        "bucket_capacity",
        "max_inflight",
        "queue_timeout_ms",
        "user_cooldown_ms",
        "wait_step_ms",
        "unavailable_ttl_ms",
        "success_hold_ttl_ms",
        "duplicate_hold_ttl_ms"
    }
    local sanitized = {}

    if type(payload) ~= "table" then
        return sanitized
    end

    for _, field in ipairs(allowed_fields) do
        local value = tonumber(payload[field])
        if value and value > 0 then
            sanitized[field] = math.floor(value)
        end
    end

    return sanitized
end

local function write_json(status, payload)
    ngx.status = status
    ngx.header["Content-Type"] = "application/json; charset=utf-8"
    ngx.say(cjson.encode(payload))
    return ngx.exit(status)
end

local function reset_session_runtime(session_id)
    dict:delete(tokens_key(session_id))
    dict:delete(tokens_ts_key(session_id))
    dict:set(inflight_key(session_id), 0)
end

local function reset_session_metrics(session_id)
    for name, _ in pairs(get_metric_snapshot(session_id)) do
        dict:delete(metric_key(session_id, name))
    end
end

local function incr_metric(session_id, name, delta)
    dict:incr(metric_key(session_id, name), delta or 1, 0)
end

local function log_decision(level, fields)
    util.log_decision(level, fields)
    if fields.session_id and fields.session_id ~= "-" then
        incr_metric(fields.session_id, fields.decision or "unknown", 1)
    end
end

local function current_tokens(session_id, now_ms, gate_config)
    local token_key = tokens_key(session_id)
    local token_ts_key = tokens_ts_key(session_id)
    local tokens = dict:get(token_key)
    local last_refill_ms = dict:get(token_ts_key)

    if tokens == nil or last_refill_ms == nil then
        tokens = gate_config.bucket_capacity
        last_refill_ms = now_ms
    end

    local elapsed_ms = now_ms - last_refill_ms
    if elapsed_ms > 0 then
        local refill = (elapsed_ms / 1000) * gate_config.token_rate
        if refill > 0 then
            tokens = math.min(gate_config.bucket_capacity, tokens + refill)
            last_refill_ms = now_ms
        end
    end

    return tokens, last_refill_ms
end

local function try_acquire_token(session_id, now_ms, gate_config)
    local token_key = tokens_key(session_id)
    local token_ts_key = tokens_ts_key(session_id)
    local tokens, last_refill_ms = current_tokens(session_id, now_ms, gate_config)
    if tokens < 1 then
        dict:set(token_key, tokens)
        dict:set(token_ts_key, last_refill_ms)
        return false, tokens
    end

    tokens = tokens - 1
    dict:set(token_key, tokens)
    dict:set(token_ts_key, last_refill_ms)
    return true, tokens
end

local function get_inflight(session_id)
    return dict:get(inflight_key(session_id)) or 0
end

local function get_tokens(session_id, gate_config)
    local current = dict:get(tokens_key(session_id))
    if current == nil then
        return gate_config.bucket_capacity
    end
    return current
end

local function mark_terminal_hold(session_id, user_id, seat_fingerprint, reason, ttl_seconds)
    dict:set(terminal_key(session_id, user_id, seat_fingerprint), reason, ttl_seconds)
end

local function mark_unavailable_hold(session_id, seat_fingerprint, ttl_seconds)
    dict:set(unavailable_key(session_id, seat_fingerprint), "1", ttl_seconds)
end

local function process_upstream_payload(payload)
    if type(payload) ~= "table" then
        return
    end

    local data = payload.data
    if type(data) ~= "table" then
        return
    end

    local session_id = ngx.ctx.seckill_gate_session_id
    local user_id = ngx.ctx.seckill_gate_user_id
    local seat_fingerprint = ngx.ctx.seckill_gate_seat_fingerprint
    local gate_config = ngx.ctx.seckill_gate_config

    if not session_id or not user_id or not seat_fingerprint or not gate_config then
        return
    end

    local biz_code = tonumber(data.code)
    if biz_code == 0 or data.success == true then
        mark_terminal_hold(session_id, user_id, seat_fingerprint, "SUCCESS", gate_config.success_hold_ttl_ms / 1000)
        incr_metric(session_id, "upstream_success", 1)
        return
    end

    if biz_code == 3 then
        mark_terminal_hold(session_id, user_id, seat_fingerprint, "DUPLICATE", gate_config.duplicate_hold_ttl_ms / 1000)
        incr_metric(session_id, "upstream_duplicate", 1)
        return
    end

    if biz_code == 2 then
        mark_unavailable_hold(session_id, seat_fingerprint, gate_config.unavailable_ttl_ms / 1000)
        incr_metric(session_id, "upstream_unavailable", 1)
        return
    end

    if biz_code == 1 then
        incr_metric(session_id, "upstream_missing_seat", 1)
        return
    end

    incr_metric(session_id, "upstream_other", 1)
end

function _M.run()
    local request_id = util.ensure_request_id()
    ngx.ctx.seckill_gate_request_id = request_id

    local user_id = ngx.req.get_headers()["X-User-Id"]
    if not user_id or user_id == "" then
        log_decision(ngx.WARN, {
            decision = "reject_invalid",
            request_id = request_id,
            user_id = "-",
            session_id = "-",
            seat_fingerprint = "-"
        })
        return util.respond(400, 400, "请求头 X-User-Id 不能为空", request_id)
    end

    local body, err = util.read_json_body()
    if not body then
        log_decision(ngx.WARN, {
            decision = "reject_invalid",
            request_id = request_id,
            user_id = user_id,
            session_id = "-",
            seat_fingerprint = "-"
        })
        return util.respond(400, 400, "请求体不是合法 JSON: " .. err, request_id)
    end

    local session_id = tonumber(body.sessionId)
    if not session_id or session_id <= 0 then
        log_decision(ngx.INFO, {
            decision = "reject_invalid",
            request_id = request_id,
            user_id = user_id,
            session_id = session_id or "-",
            seat_fingerprint = "-",
            inflight = 0,
            tokens = 0
        })
        return util.respond(400, 400, "sessionId 非法", request_id)
    end

    local gate_config = get_gate_config(session_id)
    local seat_ids = body.seatIds
    if type(seat_ids) ~= "table" or #seat_ids == 0 then
        log_decision(ngx.WARN, {
            decision = "reject_invalid",
            request_id = request_id,
            user_id = user_id,
            session_id = session_id,
            seat_fingerprint = "-"
        })
        return util.respond(400, 400, "seatIds 不能为空", request_id)
    end

    local seat_fingerprint = util.build_seat_fingerprint(session_id, seat_ids)
    local terminal_reason = dict:get(terminal_key(session_id, user_id, seat_fingerprint))
    if terminal_reason then
        log_decision(ngx.INFO, {
            decision = "reject_terminal",
            request_id = request_id,
            user_id = user_id,
            session_id = session_id,
            seat_fingerprint = seat_fingerprint,
            inflight = get_inflight(session_id),
            tokens = get_tokens(session_id, gate_config)
        })
        return util.respond(409, 409, "该选座请求已处理，请勿重复提交", request_id)
    end

    if dict:get(unavailable_key(session_id, seat_fingerprint)) then
        log_decision(ngx.INFO, {
            decision = "reject_recent_unavailable",
            request_id = request_id,
            user_id = user_id,
            session_id = session_id,
            seat_fingerprint = seat_fingerprint,
            inflight = get_inflight(session_id),
            tokens = get_tokens(session_id, gate_config)
        })
        return util.respond(409, 409, "所选座位当前不可用，请更换座位后重试", request_id)
    end

    local dedupe_key = string.format("dedupe:%d:%s:%s", session_id, tostring(user_id), seat_fingerprint)
    local dedupe_ttl = gate_config.user_cooldown_ms / 1000
    local dedupe_ok = dict:add(dedupe_key, request_id, dedupe_ttl)
    if not dedupe_ok then
        log_decision(ngx.INFO, {
            decision = "reject_dedupe",
            request_id = request_id,
            user_id = user_id,
            session_id = session_id,
            seat_fingerprint = seat_fingerprint,
            inflight = get_inflight(session_id),
            tokens = get_tokens(session_id, gate_config)
        })
        return util.respond(409, 409, "请勿重复提交相同选座请求", request_id)
    end

    ngx.ctx.seckill_gate_session_id = session_id
    ngx.ctx.seckill_gate_user_id = user_id
    ngx.ctx.seckill_gate_seat_fingerprint = seat_fingerprint
    ngx.ctx.seckill_gate_config = gate_config

    local wait_start_ms = util.now_ms()
    local deadline_ms = wait_start_ms + gate_config.queue_timeout_ms

    while util.now_ms() <= deadline_ms do
        local inflight = get_inflight(session_id)
        local inflight_dict_key = inflight_key(session_id)
        if inflight < gate_config.max_inflight then
            local new_inflight, inflight_err = dict:incr(inflight_dict_key, 1, 0)
            if new_inflight and new_inflight <= gate_config.max_inflight then
                local token_ok, tokens = try_acquire_token(session_id, util.now_ms(), gate_config)
                if token_ok then
                    ngx.ctx.seckill_gate_inflight_acquired = true
                    log_decision(ngx.INFO, {
                        decision = "allow",
                        request_id = request_id,
                        user_id = user_id,
                        session_id = session_id,
                        seat_fingerprint = seat_fingerprint,
                        wait_ms = util.now_ms() - wait_start_ms,
                        inflight = new_inflight,
                        tokens = tokens
                    })
                    return
                end

                dict:incr(inflight_dict_key, -1, 0)
            elseif new_inflight and new_inflight > gate_config.max_inflight then
                dict:incr(inflight_dict_key, -1, 0)
            elseif inflight_err then
                ngx.log(ngx.ERR, "seckill_gate inflight incr failed requestId=", request_id, " err=", inflight_err)
            end
        end

        ngx.sleep(gate_config.wait_step_ms / 1000)
    end

    log_decision(ngx.WARN, {
        decision = "reject_ratelimit",
        request_id = request_id,
        user_id = user_id,
        session_id = session_id,
        seat_fingerprint = seat_fingerprint,
        wait_ms = util.now_ms() - wait_start_ms,
        inflight = get_inflight(session_id),
        tokens = get_tokens(session_id, gate_config)
    })
    return util.respond(429, 429, "当前请求过多，请稍后重试", request_id)
end

function _M.capture_upstream_result()
    if not ngx.ctx.seckill_gate_request_id then
        return
    end

    local chunk = ngx.arg[1]
    local eof = ngx.arg[2]
    if chunk and chunk ~= "" then
        ngx.ctx.seckill_gate_response_buffer = (ngx.ctx.seckill_gate_response_buffer or "") .. chunk
    end

    if not eof then
        return
    end

    local response_body = ngx.ctx.seckill_gate_response_buffer
    ngx.ctx.seckill_gate_response_buffer = nil
    if not response_body or response_body == "" then
        return
    end

    local payload = cjson.decode(response_body)
    if not payload then
        return
    end

    process_upstream_payload(payload)
end

function _M.release_inflight()
    if not ngx.ctx.seckill_gate_inflight_acquired then
        return
    end

    local session_id = ngx.ctx.seckill_gate_session_id
    if not session_id then
        return
    end

    local current, err = dict:incr(inflight_key(session_id), -1, 0)
    if not current then
        ngx.log(ngx.ERR, "seckill_gate inflight release failed requestId=", ngx.ctx.seckill_gate_request_id or "-", " err=", err)
        return
    end

    if current < 0 then
        dict:set(inflight_key(session_id), 0)
        current = 0
    end

    ngx.ctx.seckill_gate_inflight_acquired = false
    ngx.log(ngx.INFO, "seckill_gate release requestId=", ngx.ctx.seckill_gate_request_id or "-", " inflight=", current)
end

function _M.status()
    local session_id = tonumber(ngx.var.arg_sessionId) or 1
    local gate_config = get_gate_config(session_id)
    local override_value = dict:get(override_key(session_id))
    local override_config = override_value and cjson.decode(override_value) or nil

    return write_json(200, {
        sessionId = session_id,
        config = gate_config,
        override = override_config,
        runtime = {
            inflight = get_inflight(session_id),
            tokens = get_tokens(session_id, gate_config)
        },
        counters = get_metric_snapshot(session_id),
        timestamp = util.now_ms()
    })
end

function _M.update_config()
    local payload, err = util.read_json_body()
    if not payload then
        return write_json(400, {
            code = 400,
            msg = "请求体不是合法 JSON: " .. err
        })
    end

    local session_id = tonumber(payload.sessionId)
    if not session_id or session_id <= 0 then
        return write_json(400, {
            code = 400,
            msg = "sessionId 非法"
        })
    end

    local config_update = sanitize_config_update(payload)
    if next(config_update) == nil then
        return write_json(400, {
            code = 400,
            msg = "未提供有效配置字段"
        })
    end

    local stored_override = dict:get(override_key(session_id))
    local merged_override = {}
    if stored_override then
        local decoded = cjson.decode(stored_override)
        if type(decoded) == "table" then
            merged_override = decoded
        end
    end

    for key, value in pairs(config_update) do
        merged_override[key] = value
    end

    dict:set(override_key(session_id), cjson.encode(merged_override))
    reset_session_runtime(session_id)

    return write_json(200, {
        code = 200,
        msg = "更新成功",
        data = {
            sessionId = session_id,
            config = get_gate_config(session_id),
            override = merged_override,
            timestamp = util.now_ms()
        }
    })
end

function _M.reset_session()
    local payload = {}
    if ngx.req.get_method() ~= "GET" then
        payload = util.read_json_body() or {}
    end

    local session_id = tonumber(ngx.var.arg_sessionId) or tonumber(payload.sessionId) or 1
    dict:delete(override_key(session_id))
    reset_session_runtime(session_id)
    reset_session_metrics(session_id)

    return write_json(200, {
        code = 200,
        msg = "已重置指定场次闸门状态",
        data = {
            sessionId = session_id,
            config = get_gate_config(session_id),
            timestamp = util.now_ms()
        }
    })
end

return _M
