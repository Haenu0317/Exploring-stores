-- 1.参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]

-- 1.2用户id
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId

local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)

redis.call('sadd', orderKey, userId)

return 0