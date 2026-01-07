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
    private RocketMQConfig rocketMQConfig; // 获取 Topic 配置

    // Lua 脚本实例
    private DefaultRedisScript<Long> seckillScript;

    // JVM 本地库存标记 (Key: skuId, Value: true=有货, false=无货)
    // 作用：减少对 Redis 的网络冲击，当 Redis 返回库存不足时，将此标记设为 false
    private final ConcurrentHashMap<Long, Boolean> stockMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化 Lua 脚本
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setLocation(new ClassPathResource("seckill_stock.lua"));
    }

    @Override
    public Result<String> processSeckillRequest(SeckillSubmitDTO submitDTO) {
        Long userId = 3L;
        Long skuId = submitDTO.getSkuId();
        Long orderPrice = submitDTO.getOrderPrice();
        // 1. 【第一道防线】JVM 本地内存校验
        // 如果本地标记显示无货，直接返回，不查 Redis
        if (stockMap.containsKey(skuId) && !stockMap.get(skuId)) {
            return Result.error("手慢了，商品已秒光 (Local)");
        }

        // 2. 【第二道防线】Redis Lua 原子预扣减
        // KEYS[1] = seckill:stock:{skuId}
        // KEYS[2] = seckill:order:done:{userId}:{skuId}
        List<String> keys = Collections.singletonList("seckill:stock:" + skuId);
        // 注意：Lua 脚本只需要 KEYS[1] 和 KEYS[2]，我们这里传参的时候
        // Spring Data Redis 的 execute 方法：keys 列表对应 KEYS[1]...
        // 额外的 args 对应 ARGV[1]...
        // 但我们的脚本里写死了 KEYS[2] 是基于 userId 拼接的，所以我们需要传递 userId 进去或者直接把 KEYS[2] 也算好传进去

        // *修正策略*：为了配合我们之前的 Lua 脚本 (KEYS[2] 是判定重复购买的 key)
        // 让我们显式构建 KEYS[2]
        String stockKey = "seckill:stock:" + skuId;
        String dupKey = "seckill:order:done:" + userId + ":" + skuId;
        List<String> scriptKeys = List.of(stockKey, dupKey);

        Long result = stringRedisTemplate.execute(seckillScript, scriptKeys);

        if (result == null) {
            return Result.error("系统繁忙");
        }

        // 结果判定：-1 库存不足，-2 重复购买，1 成功
        if (result == -2) {
            return Result.error("您已经购买过该商品，请勿重复抢购");
        }
        if (result == -1) {
            // 标记本地缓存为无货
            stockMap.put(skuId, false);
            return Result.error("手慢了，商品已秒光");
        }

        // 3. 【第三道防线】削峰填谷 - 发送 MQ
        // 生成 基因 ID (保证后续分库分表路由一致)
        long orderId = SnowflakeIdWorker.getInstance().nextId(userId);

        // 组装消息体
        SeckillOrderMsgDTO msgDTO = new SeckillOrderMsgDTO();
        msgDTO.setUserId(userId);
        msgDTO.setSkuId(skuId);
        msgDTO.setOrderId(orderId);
        msgDTO.setOrderPrice(orderPrice);
        // msgDTO.setActivityId(submitDTO.getActivityId()); // 如果有活动ID

        // 发送消息
        String destination = rocketMQConfig.getOrderTopic(); // 确保 Config 里配置了 Topic
        try {
            rocketMQTemplate.send(destination, MessageBuilder.withPayload(msgDTO).build());
            log.info("秒杀排队中，消息发送成功: orderId={}", orderId);
        } catch (Exception e) {
            log.error("秒杀消息发送失败", e);
            // 极端情况：Redis 扣了，MQ 没发出去 -> 导致少卖 (库存这里可以不回滚，或者依赖后续补偿)
            // 生产环境建议使用 事务消息 (Transaction Message) 保证 100% 可靠
            return Result.error("排队失败，请重试");
        }

        // 返回排队中，前端需要拿着 orderId 轮询结果
        return Result.success(String.valueOf(orderId));
    }
}