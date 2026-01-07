package com.example.seckill.order.service.impl;

import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.dto.SeckillOrderMsgDTO;
import com.example.seckill.common.dto.SeckillSubmitDTO;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.utils.SnowflakeIdWorker;
import com.example.seckill.order.config.RocketMQConfig;
import com.example.seckill.order.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private RocketMQConfig rocketMQConfig;

    private DefaultRedisScript<Long> seckillScript;

    // JVM 本地库存标记 (降级开关)
    private final ConcurrentHashMap<Long, Boolean> stockMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setLocation(new ClassPathResource("seckill_stock.lua"));
    }

    @Override
    public Result<String> processSeckillRequest(SeckillSubmitDTO submitDTO) {
        // 1. 【安全修正】从上下文获取用户ID，严禁硬编码
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        Long skuId = submitDTO.getSkuId();

        // 2. 【JVM 内存拦截】
        if (stockMap.containsKey(skuId) && !stockMap.get(skuId)) {
            return Result.error("商品已售罄 (Local)");
        }

        // 3. 【Redis Lua 原子扣减】
        // KEYS[1]: seckill:stock:{skuId}
        // KEYS[2]: seckill:order:done:{userId}:{skuId}
        String stockKey = "seckill:stock:" + skuId;
        String dupKey = "seckill:order:done:" + userId + ":" + skuId;
        List<String> scriptKeys = List.of(stockKey, dupKey);

        // 执行脚本 (-1:没货, -2:重复, 1:成功)
        Long result = stringRedisTemplate.execute(seckillScript, scriptKeys);

        if (result == null) return Result.error("抢购过于火爆，请重试");
        if (result == -2) return Result.error("您已抢购成功，请勿重复下单");
        if (result == -1) {
            stockMap.put(skuId, false); // 标记本地无货
            return Result.error("商品已售罄");
        }

        // 4. 【MQ 异步下单】
        // 生成基因ID
        long orderId = SnowflakeIdWorker.getInstance().nextId(userId);

        // 组装消息 (不传价格，价格由Consumer查库决定，防止前端篡改)
        SeckillOrderMsgDTO msgDTO = new SeckillOrderMsgDTO();
        msgDTO.setUserId(userId);
        msgDTO.setSkuId(skuId);
        msgDTO.setOrderId(orderId);
        // msgDTO.setActivityId(...);

        String topic = rocketMQConfig.getOrderTopic();
        try {
            // 发送普通消息即可，因为Redis已经扣减成功，必须尝试发消息
            // 如果发消息失败，这属于极端异常，会导致“少卖”（Redis扣了但没生成订单）
            // 企业级方案通常会加一个“本地消息表”来兜底，或者简单的 Catch 异常后回滚 Redis
            rocketMQTemplate.syncSend(topic, MessageBuilder.withPayload(msgDTO).build());
            log.info(">>> 秒杀排队成功, orderId={}, userId={}", orderId, userId);
        } catch (Exception e) {
            log.error(">>> MQ发送失败，执行回滚 Redis", e);
            // 【回滚逻辑】: 恢复 Redis 库存，删除重复购买标记
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.delete(dupKey);
            return Result.error("抢购失败，请重试");
        }

        return Result.success(String.valueOf(orderId));
    }
}