-- 1. 判定用户是否已购买 (幂等性校验)
-- 如果 Key 存在，说明已经买过了，直接返回 -2 (重复购买)
if (redis.call('exists', KEYS[2]) == 1) then
    return -2
end

-- 2. 判定库存是否充足
-- 获取当前库存，并转为数字
local stock = tonumber(redis.call('get', KEYS[1]))

if(stock == nil)then
    return -3
end

-- 如果库存不存在(nil)或者小于等于0，返回 -1 (库存不足)
if (stock <= 0) then
    return -1
end

-- 3. 执行扣减与记录
-- 库存 -1
redis.call('decr', KEYS[1])
-- 记录用户已购买 (设置一个占位符，过期时间可设为 3600秒，防止无限占用内存)
-- 实际生产中，这个 Key 的过期时间应大于活动持续时间
redis.call('setex', KEYS[2], 3600, 1)

return 1