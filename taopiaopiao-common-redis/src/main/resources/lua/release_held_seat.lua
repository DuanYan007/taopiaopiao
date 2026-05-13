-- 释放长期占座: 1 -> 0
-- KEYS[1]=sessionId
-- ARGV[1]=orderNo, ARGV[2]=seatCount, ARGV[3..]=seatIds
-- 返回: 0=成功, 1=状态异常

local sessionId = KEYS[1]
local seatCount = tonumber(ARGV[2])

for i = 3, 3 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatStateKey))
    if current == nil then
        return 1
    end
    if current ~= 1 and current ~= 0 then
        return 1
    end
end

for i = 3, 3 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatStateKey, 0)
end

return 0
