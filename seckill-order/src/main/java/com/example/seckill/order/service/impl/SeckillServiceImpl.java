package com.example.seckill.order.service.impl;

import cn.hutool.json.JSONUtil; // 引入 Hutool 工具
import com.example.seckill.common.dto.SeckillMsgDTO;
import com.example.seckill.order.service.SeckillService;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private TransactionMQProducer transactionMQProducer;

    // 从配置文件读取 Topic
    @Value("${app.rocketmq.topic}")
    private String topic;

    @Override
    public boolean seckill(int userId, int goodsId) {
        // 1. 生成唯一业务流水号 (Transaction Key)
        // 格式: tx:goodsId:userId
        // 作用: 
        //   a. 在 Listener 里作为 Redis 的 Key，防止同一个用户重复抢购同一个商品
        //   b. 在回查时，通过判断这个 Key 是否存在来决定消息状态
        String transactionKey = "tx:" + goodsId + ":" + userId;

        // 2. 封装消息体
        // 2. 【升级】使用 DTO 封装消息，不再手动拼 String
        SeckillMsgDTO msgDTO = new SeckillMsgDTO(userId, goodsId);
        String body = JSONUtil.toJsonStr(msgDTO);

        Message msg = new Message(topic, "tag_seckill", transactionKey, body.getBytes(StandardCharsets.UTF_8));
        // 这里的 UserProperty 是给 Listener 里的 Lua 脚本用的
        msg.putUserProperty("goodsId", String.valueOf(goodsId));

        try {
            // 3. 【核心一步】发送事务消息
            // 参数1: 消息对象
            // 参数2: arg (会被传递给 Listener 的 executeLocalTransaction 方法的 arg 参数)
            // 这里的逻辑是：
            //   -> 发送 Half 消息给 Broker
            //   -> 成功后，自动回调 SeckillTransactionListener.executeLocalTransaction(msg, transactionKey)
            //   -> Listener 执行 Redis Lua 脚本扣库存
            //   -> 根据 Lua 结果返回 COMMIT 或 ROLLBACK
            TransactionSendResult result = transactionMQProducer.sendMessageInTransaction(msg, transactionKey);

            System.out.printf(">>> 用户 %d 抢购请求已发出，事务状态: %s%n", userId, result.getLocalTransactionState());

            // 2. 【新增】如果事务消息提交成功，发送“延时检查消息”
            if (result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {

                // 构造一条新的消息，专门用于“检查是否超时”
                // 注意：这里换一个 Topic，或者用同一个 Topic 加不同的 Tag 区分
                // 为了清晰，我们建议用同一个 Topic，但 Tag 设为 "Tag_Check_Pay"
                Message checkMsg = new Message(topic, "Tag_Check_Pay", transactionKey, body.getBytes(StandardCharsets.UTF_8));
                // 【核心代码】设置延时等级
                // Level 3 = 10秒后消费者才能收到 (模拟 15分钟)
                // 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
                checkMsg.setDelayTimeLevel(3);

                // 发送普通消息 (不需要事务，发出去就行)
                transactionMQProducer.send(checkMsg);
                System.out.println(">>> ⏳ 延时检查消息已发出(10秒后触发)，检查订单: " + transactionKey);
            }

            // 只有当本地事务状态为 COMMIT_MESSAGE 时，才算抢购排队成功
            return result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(">>> 抢购发送异常: " + e.getMessage());
            return false;
        }
    }
}