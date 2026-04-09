-- 锁座脚本
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=lockId, ARGV[3]=seatCount, ARGV[4]=expireSeconds, ARGV[5..]=seats
-- 返回: 0=成功, 1=座位不存在, 2=座位不可用, 3=重复购票

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local lockId = tostring(ARGV[2])
local seatCount = tonumber(ARGV[3])
local expireSeconds = tonumber(ARGV[4]) or 330

if expireSeconds <= 0 then
    expireSeconds = 330
end

local function parse_lock_value(lockValue)
    local delimiter = string.find(lockValue, "|", 1, true)
    if not delimiter then
        return nil, nil
    end

    local ownerUserId = string.sub(lockValue, 1, delimiter - 1)
    local ownerLockId = string.sub(lockValue, delimiter + 1)
    return ownerUserId, ownerLockId
end

for i = 5, 5 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatStateKey))

    if current == nil then
        return 1
    end

    if current == 2 then
        return 2
    end

    local lockValue = redis.call("GET", seatLockKey)
    if lockValue then
        local ownerUserId, ownerLockId = parse_lock_value(lockValue)
        if ownerUserId == userId and ownerLockId == lockId then
            return 3
        end
        if ownerUserId == userId then
            return 3
        end
        return 2
    end
end

for i = 5, 5 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatLockKey, userId .. "|" .. lockId, "EX", expireSeconds)
end

return 0
