-- 购买确认脚本
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=seatCount, ARGV[3..]=seats
-- 返回: 0=成功, 1=无权操作或座位状态异常

local sessionId = KEYS[1]
local userId = ARGV[1]
local seatCount = tonumber(ARGV[2])
local userLockKey = "user:" .. userId .. ":locks"

for i = 3, 3 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatKey = "seat:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatKey))

    -- 已售出时直接视为成功，保证 MQ 重试时幂等
    if current == 2 then
        redis.call("HDEL", userLockKey, seatId)
    elseif redis.call("HDEL", userLockKey, seatId) == 1 then
        redis.call("SET", seatKey, 2)  -- 标记为已售出
    else
        return 1  -- 无权操作
    end
end

return 0
