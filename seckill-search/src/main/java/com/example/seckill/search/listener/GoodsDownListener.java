package com.example.seckill.search.listener;

import com.example.seckill.search.repository.GoodsRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商品下架/删除消息消费者
 * * 架构规范：
 * 1. topic: 监听 goods-down-topic (必须与生产者一致)
 * 2. consumerGroup: 建议使用独立组名 "search-down-consumer-group"，方便监控和运维区分上/下架流量
 * 3. 泛型: String (遵循我们刚才定的序列化安全规范)
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "goods-down-topic", consumerGroup = "search-down-consumer-group")
public class GoodsDownListener implements RocketMQListener<Long> {

    @Autowired
    private GoodsRepository goodsRepository;

    @Override
    public void onMessage(Long spuId) {
        log.info("MQ-Consumer: 收到商品下架消息，准备从 ES 删除，spuId={}", spuId);

        try {

            // 核心动作：从 ES 物理删除
            // deleteById 是 ElasticsearchRepository 自带的方法，若 ID 不存在也不会报错（幂等）
            goodsRepository.deleteById(spuId);

            log.info("MQ-Consumer: 商品从 ES 删除成功 (或原本就不存在)，spuId={}", spuId);
        } catch (Exception e) {
            log.error("MQ-Consumer: 下架操作失败，spuId={}, error={}", spuId, e.getMessage());
            // 抛出异常，触发 RocketMQ 重试。下架失败必须重试，否则会出现“僵尸商品”
            throw e;
        }
    }
}