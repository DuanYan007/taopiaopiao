-- TCC Cancel: 回滚临时锁，或写空回滚 marker
-- KEYS[1]=sessionId
-- ARGV[1]=userId, ARGV[2]=orderNo, ARGV[3]=xid, ARGV[4]=seatCount, ARGV[5]=cancelMarkerExpireSeconds, ARGV[6..]=seatIds
-- 返回: 0=正常回滚成功, 1=空回滚成功, 2=幂等成功, 3=资源冲突

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local orderNo = tostring(ARGV[2])
local xid = tostring(ARGV[3])
local seatCount = tonumber(ARGV[4])
local cancelMarkerExpireSeconds = tonumber(ARGV[5]) or 15

local currentSeatTry = "TRY|" .. userId .. "|" .. orderNo .. "|" .. xid
local currentSeatCancel = "CANCEL|" .. userId .. "|" .. orderNo .. "|" .. xid
local currentUserTry = "TRY|" .. orderNo .. "|" .. xid
local currentUserCancel = "CANCEL|" .. orderNo .. "|" .. xid
local userLockKey = "lock:user:" .. sessionId .. ":" .. userId

local userLockValue = redis.call("GET", userLockKey)
if userLockValue == currentUserCancel then
    return 2
end

local hasTryResource = false
if userLockValue == currentUserTry then
    hasTryResource = true
elseif userLockValue and userLockValue ~= currentUserCancel then
    return 3
end

for i = 6, 6 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local lockValue = redis.call("GET", seatLockKey)
    if lockValue == currentSeatTry then
        hasTryResource = true
    elseif lockValue == currentSeatCancel then
        -- ignore
    elseif lockValue then
        return 3
    end
end

if hasTryResource then
    for i = 6, 6 + seatCount - 1 do
        local seatId = ARGV[i]
        local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
        local lockValue = redis.call("GET", seatLockKey)
        if lockValue == currentSeatTry then
            redis.call("DEL", seatLockKey)
        end
    end
    if userLockValue == currentUserTry then
        redis.call("DEL", userLockKey)
    end
    return 0
end

for i = 6, 6 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local lockValue = redis.call("GET", seatLockKey)
    if not lockValue then
        redis.call("SET", seatLockKey, currentSeatCancel, "EX", cancelMarkerExpireSeconds)
    end
end

if not userLockValue then
    redis.call("SET", userLockKey, currentUserCancel, "EX", cancelMarkerExpireSeconds)
end

return 1
