package com.example.seckill.order.consumer;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 死信消费者：专门处理那些重试 N 次都失败的“毒消息”
 */
@Component
public class DLQConsumer {

    @Value("${app.rocketmq.namesrv-addr}")
    private String namesrvAddr;

    // 死信 Topic 的名字是固定的： %DLQ% + 原来的消费组名
    private static final String DLQ_TOPIC = "%DLQ%consumer_group_seckill_sql";

    @PostConstruct
    public void startDLQConsumer() {
        try {
            // 创建一个新的消费者组，专门处理死信
            DefaultMQPushConsumer dlqConsumer = new DefaultMQPushConsumer("consumer_group_dlq_handler");
            dlqConsumer.setNamesrvAddr(namesrvAddr);

            // 订阅死信 Topic
            dlqConsumer.subscribe(DLQ_TOPIC, "*");

            dlqConsumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt msg : msgs) {
                    try {
                        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                        System.err.println(">>> 🚨 [报警] 发现死信消息！内容: " + body);
                        System.err.println("    -> 请运维人员人工介入，检查数据库或网络状况。");

                        // TODO: 真实场景下，这里应该调用 钉钉/邮件 API 发送报警
                        // TODO: 并将 messageBody 写入 `error_log` 表

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                // 对于死信，无论处理结果如何，都返回 SUCCESS，防止再次重试导致死循环
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

            dlqConsumer.start();
            System.out.println(">>> 💀 死信消费者(DLQ Consumer) 已启动，正在监控: " + DLQ_TOPIC);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}