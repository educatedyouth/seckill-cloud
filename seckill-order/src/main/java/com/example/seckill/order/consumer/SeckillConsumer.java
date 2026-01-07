package com.example.seckill.order.consumer;

import cn.hutool.json.JSONUtil;
import com.example.seckill.common.dto.SeckillOrderMsgDTO;
import com.example.seckill.common.entity.Order;
import com.example.seckill.common.result.Result;
import com.example.seckill.order.feign.GoodsFeignClient;
import com.example.seckill.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 秒杀下单消费者
 * 核心职责：削峰填谷，将 MQ 流量转化为数据库的原子操作
 */
@Slf4j
@Component
public class SeckillConsumer {

    @Value("${app.rocketmq.namesrv-addr}")
    private String namesrvAddr;

    @Value("${app.rocketmq.topic}")
    private String topic;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private GoodsFeignClient goodsFeignClient;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    // 延时关单 Topic (建议配置在 yml 中，这里先定义常量)
    private static final String DELAY_TOPIC = "trade-order-delay-topic";

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void startConsumer() {
        try {
            consumer = new DefaultMQPushConsumer("consumer_group_seckill_core");
            consumer.setNamesrvAddr(namesrvAddr);
            consumer.subscribe(topic, "*");

            // 设置并发线程数 (保护数据库)
            consumer.setConsumeThreadMin(5);
            consumer.setConsumeThreadMax(20);

            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt msg : msgs) {
                    try {
                        String bodyJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                        log.info(">>> [秒杀消费者] 收到消息: {}", bodyJson);

                        // 1. 解析消息对象
                        SeckillOrderMsgDTO msgDTO = JSONUtil.toBean(bodyJson, SeckillOrderMsgDTO.class);
                        Long orderId = msgDTO.getOrderId();
                        Long userId = msgDTO.getUserId();
                        Long skuId = msgDTO.getSkuId();
                        Long orderPrice = msgDTO.getOrderPrice();
                        // 2. 【幂等性检查】
                        // 利用基因 ID 直接查库，如果订单已存在，说明是重复消息，直接成功
                        Order existOrder = orderMapper.selectById(orderId);
                        if (existOrder != null) {
                            log.warn(">>> 订单已存在，触发幂等逻辑，跳过。orderId={}", orderId);
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        }

                        // 3. 【远程扣减库存】
                        // 调用 Goods 服务扣减 MySQL 库存
                        Result<String> stockResult = goodsFeignClient.reduceStockDB(skuId, 1);
                        if (stockResult.getCode() != 200) {
                            log.error(">>> 扣减库存失败");
                            // 暂时策略：不重试，视为库存不足或异常，直接丢弃消息 (或者记录死信)
                            // 生产环境应发送“库存回滚”消息
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        }

                        // 4. 【本地创建订单】
                        Order order = new Order();
                        order.setId(orderId); // 使用预生成的基因ID
                        order.setUserId(userId);
                        order.setSkuId(skuId);
                        order.setCount(1);
                        order.setMoney(BigDecimal.valueOf(orderPrice));
                        order.setStatus(1); // 1-待支付
                        order.setOrderType(1); // 1-秒杀订单
                        order.setCreateTime(new Date());
                        order.setUpdateTime(new Date());

                        orderMapper.insert(order);
                        log.info(">>> 秒杀订单落库成功: orderId={}", orderId);

                        // 5. 【发送延时消息】(用于超时自动关单)
                        // RocketMQ 延时级别: 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
                        // 级别 16 = 30m (根据实际 RocketMQ 配置调整，这里假设测试用 10s = 级别3)
                        // 生产环境通常设为 30分钟
                        MessageBuilder<?> builder = MessageBuilder.withPayload(String.valueOf(orderId));
                        // 这里为了演示效果，先设为 10秒 (Level 3)，你可以改为 30分 (Level 16)
                        rocketMQTemplate.syncSend(DELAY_TOPIC, builder.build(), 3000, 3);
                        log.info(">>> 延时关单消息已发送: orderId={}", orderId);

                    } catch (Exception e) {
                        log.error(">>> 消费异常", e);
                        // 异常重试
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

            consumer.start();
            log.info(">>> 秒杀下单消费者启动成功");

        } catch (Exception e) {
            log.error("启动消费者失败", e);
        }
    }
}