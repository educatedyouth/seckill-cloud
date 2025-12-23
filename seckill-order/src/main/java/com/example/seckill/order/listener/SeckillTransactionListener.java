package com.example.seckill.order.listener;

import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Collections;

/**
 * 事务监听器
 * 作用：处理 RocketMQ 事务消息的“本地执行”和“状态回查”两个阶段
 */
@Component // 注册为 Spring 组件，这样才能被 RocketMQConfig 注入
public class SeckillTransactionListener implements TransactionListener {

    @Autowired
    private JedisPool jedisPool; // 注入 Redis 连接池，用于操作 Redis

    // Lua 脚本：保证“检查库存”、“扣减库存”、“记录流水”这三步操作是原子性的
    // 类似于数据库的锁，防止超卖
    private static final String LUA_SCRIPT =
            "if tonumber(redis.call('get', KEYS[1])) > 0 then " + // 1. 判断库存(KEYS[1])是否大于0
                    "   redis.call('decr', KEYS[1]); " +                  // 2. 如果够，库存减 1
                    "   redis.call('set', KEYS[2], '1'); " +              // 3. 记录事务流水(KEYS[2])，标记这笔单子锁库成功
                    "   redis.call('expire', KEYS[2], 600); " +           // 4. 流水设置 10 分钟过期(够回查用了)
                    "   return 1; " +                                     // 5. 返回 1 表示成功
                    "else " +
                    "   return 0; " +                                     // 6. 库存不够，返回 0 表示失败
                    "end";

    /**
     * 【阶段一：执行本地事务】
     * 触发时机：当你的代码调用 producer.sendMessageInTransaction(...) 并且 Broker 成功收到 Half 消息后
     * * @param msg 消息对象
     * @param arg 发送消息时传递的参数 (这里我们传的是 transactionKey)
     * @return 事务状态 (COMMIT提交 / ROLLBACK回滚 / UNKNOW未知)
     */
    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String transactionKey = (String) arg; // 取出流水号，如: tx:1001:user123
        String stockKey = "stock:" + msg.getUserProperty("goodsId"); // 取出库存 Key，如: stock:1001

        try (Jedis jedis = jedisPool.getResource()) { // 获取 Redis 连接
            // 执行 Lua 脚本
            Object result = jedis.eval(LUA_SCRIPT,
                    java.util.Arrays.asList(stockKey, transactionKey), // 对应脚本里的 KEYS[1], KEYS[2]
                    Collections.emptyList());

            if ("1".equals(result.toString())) {
                // Lua 返回 1：说明 Redis 扣减成功了
                System.out.println("✅ [本地事务] Redis 扣库成功，提交消息: " + transactionKey);
                // 告诉 MQ：本地成功了，你可以把消息发给消费者去写数据库了
                return LocalTransactionState.COMMIT_MESSAGE;
            } else {
                // Lua 返回 0：说明库存不足
                System.out.println("❌ [本地事务] 库存不足/失败，回滚消息: " + transactionKey);
                // 告诉 MQ：本地失败了，把刚才那条半消息删掉吧，别发给消费者
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 如果 Redis 报错或者网络断了，我们无法确定到底扣没扣成功
            // 返回 UNKNOW，让 MQ 过一会儿来调用下面的 checkLocalTransaction 查账
            return LocalTransactionState.UNKNOW;
        }
    }

    /**
     * 【阶段二：事务回查】
     * 触发时机：
     * 1. executeLocalTransaction 返回了 UNKNOW
     * 2. 或者 executeLocalTransaction 执行超时没有返回结果
     * 3. 此时 Broker 会主动发请求询问生产者：“这笔单子到底成没成？”
     */
    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        String transactionKey = msg.getKeys(); // 从消息 Key 中拿到流水号
        System.out.println("🔍 [事务回查] 检查 Key: " + transactionKey);

        try (Jedis jedis = jedisPool.getResource()) {
            // 查账逻辑：如果不确定当时有没有扣成功，就查一下那个“流水 Key”是否存在
            if (jedis.exists(transactionKey)) {
                // 流水存在，说明当时 Lua 脚本执行成功了，只是结果没传回 MQ
                return LocalTransactionState.COMMIT_MESSAGE;
            } else {
                // 流水不存在，说明当时扣库存失败了(或者根本没执行到)
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        } catch (Exception e) {
            // 如果回查的时候 Redis 还挂着，那就继续返回 UNKNOW，MQ 会稍后继续重试
            return LocalTransactionState.UNKNOW;
        }
    }
}