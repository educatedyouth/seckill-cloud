package com.example.seckill.order.service.impl;

import cn.hutool.json.JSONUtil;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.dto.SeckillOrderMsgDTO;
import com.example.seckill.common.dto.SeckillSubmitDTO;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.utils.SnowflakeIdWorker;
import com.example.seckill.order.config.RocketMQConfig;
import com.example.seckill.order.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private RocketMQConfig rocketMQConfig;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    // JVM 本地库存标记 (降级开关)
    private final ConcurrentHashMap<Long, Boolean> stockMap = new ConcurrentHashMap<>();

    @Override
    public Result<String> processSeckillRequest(SeckillSubmitDTO submitDTO) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }
        Long skuId = submitDTO.getSkuId();

        // 1. 【JVM 内存拦截】快速失败
        if (stockMap.containsKey(skuId) && !stockMap.get(skuId)) {
            return Result.error("商品已售罄 (Local)");
        }

        // 2. 准备消息数据
        // 生成订单ID
        long orderId = SnowflakeIdWorker.getInstance().nextId(userId);

        // 构建消息体 (包含价格，避免Consumer查库)
        SeckillOrderMsgDTO msgDTO = new SeckillOrderMsgDTO();
        msgDTO.setUserId(userId);
        msgDTO.setSkuId(skuId);
        msgDTO.setOrderId(orderId);
        // TODO: 实际项目中，价格应从 Redis 缓存获取或通过签名参数传入，此处模拟固定价格
        // msgDTO.setPrice(redisService.getPrice(skuId));
        msgDTO.setOrderPrice(2899L);
        // System.out.println("success");
        String topic = rocketMQConfig.getOrderTopic();
        Message<String> message = MessageBuilder.withPayload(JSONUtil.toJsonStr(msgDTO)).build();

        try {
            // 3. 【发送事务消息】
            // 这里的第三个参数 arg 可以传递给 listener，我们传 null 即可，因为信息都在 msgDTO 里
            TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction(topic, message, null);

            // 4. 【判断结果】
            // sendMessageInTransaction 会等待 executeLocalTransaction 执行完毕
            if (sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
                log.info(">>> 秒杀成功，事务消息已提交. orderId={}", orderId);
                return Result.success(String.valueOf(orderId));
            } else {
                // ROLLBACK 可能是库存不足，也可能是重复购买
                log.warn(">>> 秒杀失败 (库存不足或重复下单). orderId={}", orderId);
                // 标记本地库存可能售罄 (这里可以做得更细，根据 header 区分是无货还是重购)
                // stockMap.put(skuId, false);
                return Result.error("抢购失败");
            }

        } catch (Exception e) {
            log.error(">>> 系统异常", e);
            return Result.error("系统繁忙，请重试");
        }
        // return Result.success("success");
    }
}