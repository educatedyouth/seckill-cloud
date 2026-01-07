package com.example.seckill.order.consumer;

import com.example.seckill.common.entity.Order;
import com.example.seckill.common.utils.RedisUtil;
import com.example.seckill.order.mapper.OrderMapper;
import com.example.seckill.order.context.TableContext; // 记得复用之前的路由分表上下文
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

    @Override
    public void onMessage(String orderIdStr) {
        Long orderId = Long.valueOf(orderIdStr);
        log.info(">>> [延时关单] 收到关单请求，OrderId: {}", orderId);

        // 1. 路由分表逻辑 (必须与下单时一致)

        Order order = null;
        TableContext.set("order_tbl_" + orderId % 4);
        order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn(">>> [延时关单] 订单未找到，可能已被物理删除或分表路由错误. OrderId: {}", orderId);
            TableContext.clear();
            return;
        }

        try {
            // 2. 判断状态
            // 状态：1-新建/待支付
            if (order.getStatus() == 1) {
                // 3. 执行关单
                Order updateOrder = new Order();
                updateOrder.setId(orderId);
                updateOrder.setStatus(5); // 5-已关闭
                orderMapper.updateById(updateOrder);
                log.info(">>> [延时关单] 订单超时未支付，已关闭. OrderId: {}", orderId);

                // 4. 【核心步骤】回补 Redis 库存
                // 既然下单只扣了 Redis，关单时必须把 Redis 补回来
                String stockKey = "seckill:stock:" + order.getSkuId();
                String dupKey = "seckill:order:done:" + order.getUserId() + ":" + order.getSkuId();
                stringRedisTemplate.opsForValue().increment(stockKey);
                // 同时移除 "用户已买过" 的标记，允许该用户再次抢购
                stringRedisTemplate.delete(dupKey);
                log.info(">>> [延时关单] Redis 库存回滚成功, Key: {}", stockKey);
            } else {
                log.info(">>> [延时关单] 订单状态不是待支付，跳过处理. Status: {}", order.getStatus());
            }
        } finally {
            TableContext.clear();
        }
    }
}