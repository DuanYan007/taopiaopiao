-- 锁座并记录 Redis 锁单聚合
-- KEYS[1]=sessionId
-- ARGV:
-- 1=userId
-- 2=lockId
-- 3=orderNo
-- 4=eventId
-- 5=seatCount
-- 6=seatLockExpireSeconds
-- 7=userLockExpireSeconds
-- 8=lockOrderTtlSeconds
-- 9=unitPrice
-- 10=totalAmount
-- 11=expireTimeMillis
-- 12=createdAtMillis
-- 13=requestId
-- 14=payloadJson
-- 15=seatIdsJson
-- 16=statusCode
-- 17=paymentStatus
-- 18..=seatIds
-- 返回: 0=成功, 1=座位不存在, 2=座位不可用, 3=用户已有未终态锁单, 4=场次或eventId非法

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local lockId = tostring(ARGV[2])
local orderNo = tostring(ARGV[3])
local eventId = tostring(ARGV[4])
local seatCount = tonumber(ARGV[5])
local seatLockExpireSeconds = tonumber(ARGV[6]) or 330
local userLockExpireSeconds = tonumber(ARGV[7]) or seatLockExpireSeconds
local lockOrderTtlSeconds = tonumber(ARGV[8]) or 7200
local unitPrice = tostring(ARGV[9])
local totalAmount = tostring(ARGV[10])
local expireTimeMillis = tostring(ARGV[11])
local createdAtMillis = tostring(ARGV[12])
local requestId = tostring(ARGV[13])
local payloadJson = tostring(ARGV[14])
local seatIdsJson = tostring(ARGV[15])
local statusCode = tostring(ARGV[16])
local paymentStatus = tostring(ARGV[17])

local sessionMetaKey = "session:" .. sessionId .. ":meta"
local cachedEventId = redis.call("HGET", sessionMetaKey, "eventId")
if cachedEventId == false or cachedEventId == nil then
    return 4
end
if tostring(cachedEventId) ~= eventId then
    return 4
end

local userLockKey = "lock:user:" .. sessionId .. ":" .. userId
if redis.call("GET", userLockKey) then
    return 3
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

for i = 18, 18 + seatCount - 1 do
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
        if ownerUserId == userId or ownerLockId == lockId then
            return 3
        end
        return 2
    end
end

for i = 18, 18 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatLockKey, userId .. "|" .. lockId, "EX", seatLockExpireSeconds)
end

redis.call("SET", userLockKey, orderNo, "EX", userLockExpireSeconds)

local orderKey = "lock:order:" .. orderNo
redis.call(
    "HSET", orderKey,
    "lockId", lockId,
    "orderNo", orderNo,
    "requestId", requestId,
    "userId", userId,
    "sessionId", sessionId,
    "eventId", eventId,
    "seatIdsJson", seatIdsJson,
    "seatCount", tostring(seatCount),
    "unitPrice", unitPrice,
    "totalAmount", totalAmount,
    "status", statusCode,
    "paymentStatus", paymentStatus,
    "expireTimeMillis", expireTimeMillis,
    "createdAtMillis", createdAtMillis,
    "updatedAtMillis", createdAtMillis
)
redis.call("EXPIRE", orderKey, lockOrderTtlSeconds)

local expireKey = "lock:expire:" .. sessionId
redis.call("ZADD", expireKey, tonumber(expireTimeMillis), orderNo)

local streamKey = "stream:lock_accepted:" .. sessionId
redis.call("XADD", streamKey, "*", "payloadJson", payloadJson, "orderNo", orderNo)

return 0
