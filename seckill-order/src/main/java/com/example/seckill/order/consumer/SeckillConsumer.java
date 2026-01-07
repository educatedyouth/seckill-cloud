package com.example.seckill.order.consumer;

import cn.hutool.json.JSONUtil;
import com.example.seckill.common.dto.SeckillOrderMsgDTO;
import com.example.seckill.common.entity.Order;
import com.example.seckill.common.result.Result;
import com.example.seckill.order.context.TableContext;
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

    // 延时关单 Topic
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
                        // 建议：移除 orderPrice，改用查库或固定值，这里暂用 1 元模拟
                        BigDecimal price = new BigDecimal("1.00");

                        // -------------------------------------------------------
                        // 【核心路由逻辑】计算分表名：order_tbl_0 ~ order_tbl_3
                        // -------------------------------------------------------
                        long tableIndex = userId % 4; // 也可以用 orderId % 4，结果是一样的
                        String tableName = "order_tbl_" + tableIndex;
                        TableContext.set(tableName); // 【关键】设置 ThreadLocal
                        log.info(">>> 路由到分表: {}", tableName);

                        try {
                            // 2. 【幂等性检查】
                            // 此时 selectById 会自动变成 select ... from order_tbl_x ...
                            Order existOrder = orderMapper.selectById(orderId);
                            if (existOrder != null) {
                                log.warn(">>> 订单已存在，触发幂等逻辑，跳过。orderId={}", orderId);
                                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                            }

                            // 3. 【远程扣减库存】(Goods 服务)
                            // 注意：这里没有分布式事务，遵循“先扣库存，后建单”的顺序
                            // 如果扣库存成功但建单失败，会出现“少卖”（库存丢了），需要后续补偿或人工处理
                            // 生产环境建议使用 Seata TCC 或 事务消息 + 本地消息表
                            // Result<String> stockResult = goodsFeignClient.reduceStockDB(skuId, 1);
                            // if (stockResult == null || stockResult.getCode() != 200) {
                            //    log.error(">>> 扣减库存失败");
                            //    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS; // 业务失败，不再重试
                            //}
                            log.info(">>> (模拟) 远程扣减库存成功");

                            // 4. 【本地创建订单】
                            Order order = new Order();
                            order.setId(orderId);
                            order.setUserId(userId);
                            order.setSkuId(skuId);
                            order.setCount(1);
                            order.setMoney(price);
                            order.setStatus(1); // 1-待支付
                            order.setOrderType(1); // 1-秒杀订单
                            order.setCreateTime(new Date());
                            order.setUpdateTime(new Date());

                            // 执行插入 (MP 会拦截并替换表名)
                            orderMapper.insert(order);
                            log.info(">>> 秒杀订单落库成功: orderId={}, table={}", orderId, tableName);

                            // 5. 【发送延时消息】(用于超时自动关单)
                            // 30分钟 = Level 16 (1s 5s ... 10m 20m 30m)
                            try {
                                MessageBuilder<?> builder = MessageBuilder.withPayload(String.valueOf(orderId));
                                rocketMQTemplate.syncSend(DELAY_TOPIC, builder.build(), 3000, 3);
                                log.info(">>> 延时关单消息已发送");
                            } catch (Exception e) {
                                log.error(">>> 延时消息发送失败，可能导致无法自动关单", e);
                                // 这里可以不阻断主流程，依靠定时任务兜底
                            }

                        } finally {
                            // 【必须】清理 ThreadLocal，防止线程复用导致数据污染
                            TableContext.clear();
                        }

                    } catch (Exception e) {
                        log.error(">>> 消费异常", e);
                        // 异常重试 (RocketMQ 默认重试 16 次)
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