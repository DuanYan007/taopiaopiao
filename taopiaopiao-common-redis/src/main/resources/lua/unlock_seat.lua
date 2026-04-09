-- 释放座位脚本
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=lockId, ARGV[3]=seatCount, ARGV[4..]=seats
-- 返回: 释放的座位数量

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local lockId = tostring(ARGV[2])
local seatCount = tonumber(ARGV[3])
local unlocked = 0

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
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local lockValue = redis.call("GET", seatLockKey)

    if not lockValue then
        unlocked = unlocked + 1
    else
        local ownerUserId, ownerLockId = parse_lock_value(lockValue)
        if ownerUserId == userId and ownerLockId == lockId then
            redis.call("DEL", seatLockKey)
            unlocked = unlocked + 1
        end
    end
end

return unlocked
