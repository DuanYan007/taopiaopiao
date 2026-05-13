-- 支付成功后把长期占座改成已售出
-- KEYS[1]=sessionId
-- ARGV[1]=userId, ARGV[2]=orderNo, ARGV[3]=seatCount, ARGV[4..]=seatIds
-- 返回: 0=成功, 1=无权操作或状态异常

local sessionId = KEYS[1]
local seatCount = tonumber(ARGV[3])

for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatStateKey))
    if current == nil or current ~= 1 then
        if current ~= 2 then
            return 1
        end
    end
end

for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatStateKey, 2)
end

return 0
