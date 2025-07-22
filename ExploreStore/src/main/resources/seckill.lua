-- 1. 参数列表
--1.1优惠卷id
local voucherId = ARGV[1]
--1.2 用户id
local userId = ARGV[2]
--1.3订单id
local orderId =ARGV[3]

-- 2. 数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3. 脚本业务逻辑

-- 3.1 判断库存是否充足
local stock = tonumber(redis.call('get', stockKey))
if not stock or stock <= 0 then
    return 1 -- 库存不足
end

-- 3.2 判断是否已下单
if redis.call('sismember', orderKey, userId) == 1 then
    return 2 -- 重复下单
end

-- 3.3 扣减库存
redis.call('decr', stockKey)

-- 3.4 保存订单信息
redis.call('sadd', orderKey, userId)

--3.6发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0 -- 成功




























