local cjson = require("cjson.safe")

local _M = {}

function _M.now_ms()
    return math.floor(ngx.now() * 1000)
end

function _M.ensure_request_id()
    local request_id = ngx.req.get_headers()["X-Request-Id"]
    if not request_id or request_id == "" then
        request_id = string.format(
                "or-%d-%d-%04d",
                ngx.worker.pid(),
                _M.now_ms(),
                math.random(0, 9999)
        )
        ngx.req.set_header("X-Request-Id", request_id)
    end

    ngx.header["X-Request-Id"] = request_id
    return request_id
end

function _M.read_json_body()
    ngx.req.read_body()

    local body = ngx.req.get_body_data()
    if not body then
        local body_file = ngx.req.get_body_file()
        if body_file then
            local file = io.open(body_file, "rb")
            if file then
                body = file:read("*a")
                file:close()
            end
        end
    end

    if not body or body == "" then
        return nil, "empty request body"
    end

    local decoded, err = cjson.decode(body)
    if not decoded then
        return nil, err or "invalid json"
    end

    return decoded
end

function _M.normalize_seat_ids(seat_ids)
    local normalized = {}
    if type(seat_ids) ~= "table" then
        return normalized
    end

    for _, seat_id in ipairs(seat_ids) do
        normalized[#normalized + 1] = tostring(seat_id)
    end

    table.sort(normalized)
    return normalized
end

function _M.build_seat_fingerprint(session_id, seat_ids)
    local normalized = _M.normalize_seat_ids(seat_ids)
    return string.format("%s:%s", tostring(session_id), table.concat(normalized, ","))
end

function _M.respond(status, code, msg, request_id)
    ngx.status = status
    ngx.header["Content-Type"] = "application/json; charset=utf-8"
    if request_id and request_id ~= "" then
        ngx.header["X-Request-Id"] = request_id
    end

    ngx.say(cjson.encode({
        code = code,
        msg = msg,
        data = cjson.null,
        timestamp = _M.now_ms()
    }))

    return ngx.exit(status)
end

function _M.log_decision(level, fields)
    local message = string.format(
            "seckill_gate decision=%s requestId=%s userId=%s sessionId=%s seatFingerprint=%s waitMs=%s inflight=%s tokens=%s",
            fields.decision or "unknown",
            fields.request_id or "-",
            fields.user_id or "-",
            fields.session_id or "-",
            fields.seat_fingerprint or "-",
            fields.wait_ms or 0,
            fields.inflight or 0,
            fields.tokens or 0
    )
    ngx.log(level, message)
end

return _M
