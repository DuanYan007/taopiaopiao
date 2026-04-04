-- 锁座脚本
-- 参数: KEYS[1]=sessionId, ARGV[1]=userId, ARGV[2]=seatCount, ARGV[3]=expireSeconds, ARGV[4..]=seats
-- 返回: 0=成功, 1=座位不存在, 2=座位不可用, 3=重复购票

local sessionId = KEYS[1]
local userId = ARGV[1]
local seatCount = tonumber(ARGV[2])
local expireSeconds = tonumber(ARGV[3]) or 300  -- 默认300秒
local userLockKey = "user:" .. userId .. ":locks"

-- 确保 expireSeconds 有效
if expireSeconds <= 0 then
    expireSeconds = 300
end

-- 第一阶段：完整校验，确保不会出现部分写入

-- 检查重复购票
for i = 4, 4 + seatCount - 1 do
    if redis.call("HEXISTS", userLockKey, ARGV[i]) == 1 then
        return 3
    end
end

for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatKey = "seat:" .. sessionId .. ":" .. seatId
    local current = tonumber(redis.call("GET", seatKey))

    if current == nil then
        return 1  -- 座位不存在
    end

    if current ~= 0 then
        return 2  -- 座位不可用
    end
end

-- 第二阶段：统一写入，避免前半段成功后中途失败留下脏数据
for i = 4, 4 + seatCount - 1 do
    local seatId = ARGV[i]
    local seatKey = "seat:" .. sessionId .. ":" .. seatId
    redis.call("SET", seatKey, 1, "EX", expireSeconds)
    redis.call("HSET", userLockKey, seatId, "1")
end

return 0
