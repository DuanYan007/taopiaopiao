-- 锁座并记录用户锁索引
-- KEYS[1]=sessionId
-- ARGV:
-- 1=userId
-- 2=orderNo
-- 3=xid
-- 4=eventId
-- 5=seatCount
-- 6=seatLockExpireSeconds
-- 7=userLockExpireSeconds
-- 8..=seatIds
-- 返回: 0=成功, 1=座位不存在, 2=座位不可用, 3=用户已有未终态锁单, 4=场次或eventId非法, 5=幂等成功, 6=命中空回滚 marker, 7=被其他事务锁定

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local orderNo = tostring(ARGV[2])
local xid = tostring(ARGV[3])
local eventId = tostring(ARGV[4])
local seatCount = tonumber(ARGV[5])
local seatLockExpireSeconds = tonumber(ARGV[6]) or 330
local userLockExpireSeconds = tonumber(ARGV[7]) or seatLockExpireSeconds

local currentSeatTry = "TRY|" .. userId .. "|" .. orderNo .. "|" .. xid
local currentSeatCancel = "CANCEL|" .. userId .. "|" .. orderNo .. "|" .. xid
local currentUserTry = "TRY|" .. orderNo .. "|" .. xid
local currentUserCancel = "CANCEL|" .. orderNo .. "|" .. xid

local sessionMetaKey = "session:" .. sessionId .. ":meta"
local cachedEventId = redis.call("HGET", sessionMetaKey, "eventId")
if cachedEventId == false or cachedEventId == nil then
    return 4
end
if tostring(cachedEventId) ~= eventId then
    return 4
end

local userLockKey = "lock:user:" .. sessionId .. ":" .. userId
local userLockValue = redis.call("GET", userLockKey)
if userLockValue then
    if userLockValue == currentUserTry then
        return 5
    end
    if userLockValue == currentUserCancel then
        return 6
    end
    return 3
end

for i = 8, 8 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatStateKey))

    if current == nil then
        return 1
    end

    if current ~= 0 then
        return 2
    end

    local lockValue = redis.call("GET", seatLockKey)
    if lockValue then
        if lockValue == currentSeatTry then
            return 5
        end
        if lockValue == currentSeatCancel then
            return 6
        end
        return 7
    end
end

for i = 8, 8 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatLockKey, currentSeatTry, "EX", seatLockExpireSeconds)
end

redis.call("SET", userLockKey, currentUserTry, "EX", userLockExpireSeconds)

return 0
