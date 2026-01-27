package com.example.seckill.search.listener;

import com.example.seckill.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate; // 【新增】
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(topic = "goods-up-topic", consumerGroup = "search-consumer-group")
public class GoodsUpListener implements RocketMQListener<String> {

    @Autowired
    private SearchService searchService;

    @Autowired
    private RocketMQTemplate rocketMQTemplate; // 【新增】用于发送 AI 任务

    @Override
    public void onMessage(String spuIdStr) {
        log.info("MQ-Consumer: [阶段1] 收到上架消息 spuId={}", spuIdStr);

        try {
            Long spuId = Long.valueOf(spuIdStr);

            // 1. 执行快速同步 (100ms 内)
            boolean success = searchService.syncUp(spuId);

            if (success) {
                // 2. 发送异步消息触发 AI 增强
                // 发送到独立的 topic: goods-up-ai-topic
                try {
                    rocketMQTemplate.convertAndSend("goods-up-ai-topic", spuIdStr);
                    log.info(">>> 已投递 AI 增强任务: spuId={}", spuId);
                } catch (Exception ex) {
                    log.error(">>> AI 任务投递失败", ex);
                    // 这里的失败不抛出异常，因为基础数据已经上架成功，不能因为 AI 挂了导致商品回滚
                }
            } else {
                log.warn("基础同步失败，不投递 AI 任务");
                throw new RuntimeException("Sync Basic Failed");
            }
        } catch (Exception e) {
            log.error("消费失败重试 spuId={}", spuIdStr, e);
            throw e;
        }
    }
}