-- 购买确认脚本
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=lockId, ARGV[3]=seatCount, ARGV[4..]=seats
-- 返回: 0=成功, 1=无权操作或座位状态异常

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local lockId = tostring(ARGV[2])
local seatCount = tonumber(ARGV[3])

local function parse_lock_value(lockValue)
    local delimiter = string.find(lockValue, "|", 1, true)
    if not delimiter then
        return nil, nil
    end

    local ownerUserId = string.sub(lockValue, 1, delimiter - 1)
    local ownerLockId = string.sub(lockValue, delimiter + 1)
    return ownerUserId, ownerLockId
end

for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatStateKey))

    if current == nil then
        return 1
    end

    if current ~= 2 then
        local lockValue = redis.call("GET", seatLockKey)
        if not lockValue then
            return 1
        end

        local ownerUserId, ownerLockId = parse_lock_value(lockValue)
        if ownerUserId ~= userId or ownerLockId ~= lockId then
            return 1
        end
    end
end

for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatStateKey, 2)
    redis.call("DEL", seatLockKey)
end

return 0
