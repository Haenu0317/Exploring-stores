-- 1.参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.1 订单key
local orderKey = 'seckill:order:' .. voucherId

--3.脚本业务
--3.1.判断用户是否库存充足 get stockKey
if(tonumber(redis.call('get',stockKey)) <= 0)then
    --库存不足返回一
    return 1
end

--判断用户是否下单
if(redis.call('sismember',orderKey,userId) == 1)then
    return 2
end

--用户没有下单
--扣减库存
redis.call('incrby',stockKey,-1)
--将用户添加到set集合中
redis.call('sadd',orderKey,userId)

return 0