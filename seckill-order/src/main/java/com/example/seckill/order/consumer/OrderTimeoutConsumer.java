package com.example.seckill.order.consumer;

import com.example.seckill.common.entity.Order;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.utils.RedisUtil;
import com.example.seckill.order.context.TableContext;
import com.example.seckill.order.feign.GoodsFeignClient;
import com.example.seckill.order.feign.PayFeignClient;
import com.example.seckill.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单超时自动取消消费者
 * 监听 Topic: trade-order-delay-topic
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "trade-order-delay-topic",
        consumerGroup = "order_timeout_group"
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 【新增】注入支付 Feign 客户端
    @Autowired
    private PayFeignClient payFeignClient;
    @Autowired
    private GoodsFeignClient goodsFeignClient; // 用于扣减库存
    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.valueOf(orderIdStr);
        log.info(">>> [延时关单] 收到关单请求，OrderId: {}", orderId);

        // 1. 路由分表逻辑 (假设 orderId 包含分片键或算法与 userId 一致)
        // 注意：生产环境如果按 UserId 分表，这里必须解析出 UserId 才能找到正确的表
        TableContext.set("order_tbl_" + orderId % 4);

        try {
            Order order = orderMapper.selectById(orderId);

            if (order == null) {
                log.warn(">>> [延时关单] 订单未找到，可能已被物理删除或分表路由错误. OrderId: {}", orderId);
                return;
            }

            // 2. 判断状态：只有 "1-待支付" 的订单才需要处理
            if (order.getStatus() == 1) {

                // ===========================================================================
                // 【核心对接】: 关单前的最终防线 —— 调用第三方支付查询
                // ===========================================================================
                boolean isPaidRemote = false;
                try {
                    log.info(">>> [延时关单] 订单 {} 本地未支付，正在核实第三方支付状态...", orderId);
                    Result<Boolean> payResult = payFeignClient.checkPayStatus(orderId);
                    if (payResult != null && Boolean.TRUE.equals(payResult.getData())) {
                        isPaidRemote = true;
                    }
                } catch (Exception e) {
                    log.error(">>> [延时关单] 调用支付接口失败，为安全起见，本次暂不关单，等待下次重试或人工处理", e);
                    // 策略：如果支付服务挂了，最好不要贸然关单，可以抛出异常让 MQ 重试，或者记录告警
                    // 这里为了演示简单，选择抛出异常触发 MQ 重试
                    throw new RuntimeException("支付服务不可用，触发重试");
                }

                if (isPaidRemote) {
                    // A. 如果第三方说“已支付” -> 修正本地状态
                    log.info(">>> [延时关单] 核实发现订单 {} 实际已支付！正在修正本地状态...", orderId);
                    goodsFeignClient.reduceStockDB(order.getSkuId(),1);
                    log.info(">>> 远程扣减库存成功");
                    Order updateOrder = new Order();
                    updateOrder.setId(orderId);
                    updateOrder.setStatus(2); // 2-已支付
                    orderMapper.updateById(updateOrder);
                } else {
                    // B. 如果第三方也说“未支付” -> 真的超时了，执行关单 + 回滚库存
                    performCloseOrder(order);
                }

            } else {
                log.info(">>> [延时关单] 订单状态不是待支付，跳过处理. Status: {}", order.getStatus());
            }
        } finally {
            // 【必须】清理 ThreadLocal
            TableContext.clear();
        }
    }

    /**
     * 执行物理关单与库存回滚
     */
    private void performCloseOrder(Order order) {
        // 1. 更新数据库状态为 5-已关闭
        Order updateOrder = new Order();
        updateOrder.setId(order.getId());
        updateOrder.setStatus(5);
        orderMapper.updateById(updateOrder);
        log.info(">>> [延时关单] 订单超时未支付，已执行关闭. OrderId: {}", order.getId());

        // 2. 【核心步骤】回补 Redis 库存
        // 既然下单只扣了 Redis，关单时必须把 Redis 补回来
        String stockKey = "seckill:stock:" + order.getSkuId();

        // 2.1 库存 +1
        stringRedisTemplate.opsForValue().increment(stockKey);

        // 2.2 移除“用户已购买”标记 (Allow user to buy again)
        String dupKey = "seckill:order:done:" + order.getUserId() + ":" + order.getSkuId();
        stringRedisTemplate.delete(dupKey);

        log.info(">>> [延时关单] Redis 库存回滚成功, Key: {}, DupKey已移除", stockKey);
    }
}