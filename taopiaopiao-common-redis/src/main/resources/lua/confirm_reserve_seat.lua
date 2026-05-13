-- TCC Confirm: 临时锁转长期占座
-- KEYS[1]=sessionId
-- ARGV[1]=userId, ARGV[2]=orderNo, ARGV[3]=xid, ARGV[4]=seatCount, ARGV[5..]=seatIds
-- 返回: 0=成功, 1=幂等成功, 2=已回滚, 3=非法状态

local sessionId = KEYS[1]
local userId = tostring(ARGV[1])
local orderNo = tostring(ARGV[2])
local xid = tostring(ARGV[3])
local seatCount = tonumber(ARGV[4])

local currentSeatTry = "TRY|" .. userId .. "|" .. orderNo .. "|" .. xid
local currentSeatCancel = "CANCEL|" .. userId .. "|" .. orderNo .. "|" .. xid
local currentUserTry = "TRY|" .. orderNo .. "|" .. xid
local currentUserCancel = "CANCEL|" .. orderNo .. "|" .. xid
local userLockKey = "lock:user:" .. sessionId .. ":" .. userId

local function all_reserved()
    for i = 5, 5 + seatCount - 1 do
        local seatId = ARGV[i]
        local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
        local current = tonumber(redis.call("GET", seatStateKey))
        if current ~= 1 then
            return false
        end
    end
    return true
end

local userLockValue = redis.call("GET", userLockKey)
if userLockValue == false or userLockValue == nil then
    if all_reserved() then
        return 1
    end
    return 3
end
if userLockValue == currentUserCancel then
    return 2
end
if userLockValue ~= currentUserTry then
    return 3
end

for i = 5, 5 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    local currentState = tonumber(redis.call("GET", seatStateKey))
    local currentLock = redis.call("GET", seatLockKey)

    if currentLock then
        if currentLock ~= currentSeatTry then
            return 3
        end
    elseif currentState ~= 1 then
        return 3
    end
end

for i = 5, 5 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatStateKey = "seat:state:" .. sessionId .. ":" .. seatId
    local seatLockKey = "seat:lock:" .. sessionId .. ":" .. seatId
    redis.call("DEL", seatLockKey)
    redis.call("SET", seatStateKey, 1)
end

redis.call("DEL", userLockKey)
return 0
