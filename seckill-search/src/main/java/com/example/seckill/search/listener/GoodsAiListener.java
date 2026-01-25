package com.example.seckill.search.listener;

import com.example.seckill.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 阶段二：AI 增强任务消费者
 * 独立 Topic，独立 Group，慢速消费，不阻塞主链路
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "goods-up-ai-topic", consumerGroup = "search-ai-consumer-group")
public class GoodsAiListener implements RocketMQListener<String> {

    @Autowired
    private SearchService searchService;

    @Override
    public void onMessage(String spuIdStr) {
        log.info("MQ-Consumer: [阶段2] 收到 AI 增强任务 spuId={}", spuIdStr);
        try {
            Long spuId = Long.valueOf(spuIdStr);
            // 调用慢逻辑
            searchService.syncAiStrategy(spuId);
        } catch (Exception e) {
            // AI 任务如果失败，可以根据策略决定是否重试。
            // 通常建议打印 Error 即可，不要死循环重试，因为 LLM 挂了可能一直挂。
            log.error("AI 增强任务执行异常 spuId={}", spuIdStr, e);
        }
    }
}