package com.example.seckill.order.listener;

import cn.hutool.json.JSONUtil;
import com.example.seckill.common.dto.SeckillOrderMsgDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 秒杀事务消息监听器
 * 核心作用：将 Redis 扣减与 MQ 发送绑定为原子操作
 */
@Slf4j
@Component
@RocketMQTransactionListener // 自动关联 RocketMQTemplate
public class SeckillTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> seckillScript;

    @PostConstruct
    public void init() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setResultType(Long.class);
        seckillScript.setLocation(new ClassPathResource("seckill_stock.lua"));
    }

    /**
     * 【阶段一：执行本地事务】
     * 收到 Half Message 后回调此方法。在这里执行 Redis 扣减。
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            // 1. 解析消息
            String bodyJson = new String((byte[]) msg.getPayload(), StandardCharsets.UTF_8);
            SeckillOrderMsgDTO msgDTO = JSONUtil.toBean(bodyJson, SeckillOrderMsgDTO.class);
            Long userId = msgDTO.getUserId();
            Long skuId = msgDTO.getSkuId();

            // 2. 准备 Redis Key
            String stockKey = "seckill:stock:" + skuId;
            String dupKey = "seckill:order:done:" + userId + ":" + skuId;
            List<String> keys = List.of(stockKey, dupKey);

            // 3. 执行 Lua 脚本
            // 返回值: 1=成功, -1=无库存, -2=重复购买, -3=Key不存在
            Long result = stringRedisTemplate.execute(seckillScript, keys);

            if (result != null && result != -1) {
                log.info("✅ [本地事务] Redis扣减成功, 提交消息. orderId={}", msgDTO.getOrderId());
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                if (result != null && result == -1) {
                    log.warn("❌ [本地事务] 库存不足. skuId={}", skuId);
                }
                // 为了压测，允许重复下单
                // else if (result != null && result == -2) {
                //     log.warn("❌ [本地事务] 重复下单. userId={}", userId);
                // }
                // 扣减失败，回滚消息 (MQ 不会把消息发给 Consumer)
                return RocketMQLocalTransactionState.ROLLBACK;
            }

        } catch (Exception e) {
            log.error(">>> 执行本地事务异常", e);
            // 发生异常（如 Redis 连不上），为了保险起见，返回 ROLLBACK
            // 或者返回 UNKNOWN 让 MQ 稍后回查（但对于秒杀，fail-fast 更好）
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 【阶段二：事务回查】
     * 如果 executeLocalTransaction 返回 UNKNOWN，或者超时未响应，MQ 会调用此方法。
     * 检查 Redis 中是否有“购买成功标记”，以确定当时到底扣没扣成功。
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            String bodyJson = new String((byte[]) msg.getPayload(), StandardCharsets.UTF_8);
            SeckillOrderMsgDTO msgDTO = JSONUtil.toBean(bodyJson, SeckillOrderMsgDTO.class);

            // 检查重复购买 Key 是否存在
            // 这个 Key 是 Lua 脚本中扣减成功后写入的
            String dupKey = "seckill:order:done:" + msgDTO.getUserId() + ":" + msgDTO.getSkuId();
            Boolean hasBought = stringRedisTemplate.hasKey(dupKey);

            if (Boolean.TRUE.equals(hasBought)) {
                log.info("🔍 [事务回查] 订单标记存在，提交消息. orderId={}", msgDTO.getOrderId());
                return RocketMQLocalTransactionState.COMMIT;
            } else {
                log.warn("🔍 [事务回查] 订单标记不存在，回滚消息. orderId={}", msgDTO.getOrderId());
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        } catch (Exception e) {
            log.error(">>> 事务回查异常", e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}