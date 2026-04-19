-- 更新 Redis 锁单状态
-- KEYS[1]=orderNo
-- ARGV:
-- 1=expectedCount
-- 2..N=expectedStatuses
-- N+1=targetStatus
-- N+2=paymentStatus
-- N+3=failReason
-- N+4=updatedAtMillis
-- N+5=clearUserLockIndex(0/1)
-- N+6=ttlSeconds
-- 返回: 1=成功, 0=未命中

local orderNo = KEYS[1]
local orderKey = "lock:order:" .. orderNo
if redis.call("EXISTS", orderKey) == 0 then
    return 0
end

local expectedCount = tonumber(ARGV[1])
local currentStatus = tostring(redis.call("HGET", orderKey, "status"))
local matched = false
for i = 2, 1 + expectedCount do
    if currentStatus == tostring(ARGV[i]) then
        matched = true
        break
    end
end

if not matched then
    return 0
end

local targetStatus = tostring(ARGV[expectedCount + 2])
local paymentStatus = tostring(ARGV[expectedCount + 3] or "")
local failReason = tostring(ARGV[expectedCount + 4] or "")
local updatedAtMillis = tostring(ARGV[expectedCount + 5])
local clearUserLockIndex = tostring(ARGV[expectedCount + 6]) == "1"
local ttlSeconds = tonumber(ARGV[expectedCount + 7]) or 7200

redis.call("HSET", orderKey, "status", targetStatus, "updatedAtMillis", updatedAtMillis)

if paymentStatus ~= "" then
    redis.call("HSET", orderKey, "paymentStatus", paymentStatus)
end

if failReason ~= "" then
    redis.call("HSET", orderKey, "failReason", failReason)
else
    redis.call("HDEL", orderKey, "failReason")
end

redis.call("EXPIRE", orderKey, ttlSeconds)

if clearUserLockIndex then
    local sessionId = redis.call("HGET", orderKey, "sessionId")
    local userId = redis.call("HGET", orderKey, "userId")
    if sessionId and userId then
        redis.call("DEL", "lock:user:" .. sessionId .. ":" .. userId)
        redis.call("ZREM", "lock:expire:" .. sessionId, orderNo)
    end
end

return 1
