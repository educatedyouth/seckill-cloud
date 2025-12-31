package com.example.seckill.search.listener;

import com.example.seckill.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商品上架消息消费者
 * 架构规范：
 * 1. topic: 必须与生产者一致。
 * 2. consumerGroup: 必须独立，防止与其他服务的消费者冲突。
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "goods-up-topic", consumerGroup = "search-consumer-group")
public class GoodsUpListener implements RocketMQListener<Long> {

    @Autowired
    private SearchService searchService;

    @Override
    public void onMessage(Long spuId) {
        log.info("MQ-Consumer: 收到商品上架消息，spuId={}", spuId);

        // 鲁棒性设计：异常重试
        // RocketMQ 默认机制：如果这里抛出异常，MQ 会进行重试 (1s, 5s, 10s...)，直到成功或进入死信队列。
        try {
            boolean success = searchService.syncUp(spuId);
            if (!success) {
                log.warn("同步逻辑返回 false，主动抛出异常以触发 MQ 重试，spuId={}", spuId);
                throw new RuntimeException("Sync Failed");
            }
            log.info("MQ-Consumer: 商品同步 ES 成功，spuId={}", spuId);
        } catch (Exception e) {
            log.error("MQ-Consumer: 消费失败，正在重试... spuId={}, error={}", spuId, e.getMessage());
            throw e; // 关键：必须抛出异常，RocketMQ 才知道消费失败了
        }
    }
}