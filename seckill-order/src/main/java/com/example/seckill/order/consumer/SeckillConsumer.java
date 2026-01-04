package com.example.seckill.order.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.seckill.common.dto.SeckillMsgDTO;
import com.example.seckill.common.entity.Order;
import com.example.seckill.order.mapper.OrderMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * 消费者：负责将 MQ 中的订单消息写入 MySQL
 */
@Component
public class SeckillConsumer {

    // ... 注入 JedisPool
    @Autowired
    private JedisPool jedisPool;

    @Value("${app.rocketmq.namesrv-addr}")
    private String namesrvAddr;

    @Value("${app.rocketmq.topic}")
    private String topic;

    @Autowired
    private OrderMapper orderMapper;

    private DefaultMQPushConsumer consumer;

    // @PostConstruct 表示在 Spring 容器启动并注入完依赖后，自动执行该方法
    @PostConstruct
    public void startConsumer() {
        try {
            // 1. 创建消费者，指定消费组名
            consumer = new DefaultMQPushConsumer("consumer_group_seckill_sql");
            consumer.setNamesrvAddr(namesrvAddr);

            // 2. 订阅 Topic (接收所有 Tag)
            consumer.subscribe(topic, "*");

            // 3. 【削峰关键】配置消费线程数
            // 即使 MQ 里积压了 10万条消息，这里也只用 5~10 个线程慢慢处理，保护 MySQL 不被压垮
            consumer.setConsumeThreadMin(5);
            consumer.setConsumeThreadMax(10);

            // 【新增】配置最大重试次数为 1 次 (默认是 16 次)
            // 意味着：第一次失败后，只重试 1 次。如果还失败，直接进死信队列。
            // consumer.setMaxReconsumeTimes(1);

            // 4. 注册监听器 (并发消费模式)
            consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
                for (MessageExt msg : msgs) {
                    try {
                        String bodyJson = new String(msg.getBody(), StandardCharsets.UTF_8);
                        // 【核心修复】定义在循环外，初始化为空
                        SeckillMsgDTO dto = null;

                        try {
                            // 【升级】使用 Hutool 解析 JSON 对象
                            // 1. 尝试解析 JSON
                            // 如果这条消息是旧格式 "2002,1001"，这里会立刻报错，跳到 catch
                            dto = JSONUtil.toBean(bodyJson, SeckillMsgDTO.class);
                        } catch (Exception e) {
                            // 2. 捕获解析异常 (处理脏数据)
                            System.err.println(">>> ⚠️ [兼容报警] 发现旧格式/非法消息，已忽略。内容: " + bodyJson);
                            // 返回 SUCCESS 是为了告诉 MQ "这条烂消息我吃掉了，别再发给我了"
                            // 如果返回 RECONSUME_LATER，它一会还会来报错，死循环
                            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                        }
                        System.out.println(">>> 消费者收到消息: " + dto);
                        System.out.println(">>> Tags为: " + msg.getTags());
                        int userId = dto.getUserId(); // 注意这里解析要根据你的实际 body 格式微调
                        int goodsId = dto.getGoodsId(); // 如果你上一步发的 body 格式变了，这里也要变
                        // --- 分支 1: 处理下单消息 ---
                        if ("tag_seckill".equals(msg.getTags()) || msg.getTags() == null) {
                            System.out.println(">>> [下单] 收到下单请求: " + dto);
                            // 【新增】模拟业务处理慢，每个单子处理 100ms
                            // 这样单线程 TPS 只有 10，很容易积压
                            try { Thread.sleep(100); } catch (InterruptedException e) {}
                            Order order = new Order();
                            order.setUserId(userId);
                            order.setGoodsId(goodsId);
                            order.setCreateTime(new Date());
                            order.setStatus(0); // 0 未支付
                            orderMapper.insert(order);
                        }

                        // --- 分支 2: 处理延时关单消息 ---
                        else if ("Tag_Check_Pay".equals(msg.getTags())) {

                            // // 1. 【新增】获取当前重试次数 (第一次接收是 0)
                            // int times = msg.getReconsumeTimes();
                            // System.out.println(">>> [超时检查] 收到消息，当前重试次数: " + times);
                            //
                            // // 2. 【新增】模拟前 3 次数据库挂了 (重试 0, 1, 2 次都抛异常)
                            // if (times < 3) {
                            //     System.err.println("    -> 💥 模拟数据库故障，抛出异常，触发 RocketMQ 重试！");
                            //     // 抛出运行时异常，RocketMQ 会自动捕获并安排重试
                            //     throw new RuntimeException("数据库连接失败 (模拟)");
                            // }
                            System.out.println(">>> [超时检查] 收到延时检查消息, 10秒已到! 正在检查用户: " + userId);

                            // 1. 【真实查询】去 MySQL 查该用户是否有“未支付”的订单
                            // SQL: SELECT * FROM order_tbl WHERE user_id=? AND goods_id=? AND status=0 LIMIT 1
                            QueryWrapper<Order> query = new QueryWrapper<>();
                            query.eq("user_id", userId);
                            query.eq("goods_id", goodsId);
                            query.eq("status", 0); // 重点：只查 status=0 (未支付) 的单子

                            // 查出所有符合条件的未支付订单（可能是一条，也可能是多条脏数据）
                            List<Order> orders = orderMapper.selectList(query);

                            if (orders != null && !orders.isEmpty()) {
                                System.out.println("    -> 发现 " + orders.size() + " 条未支付订单，准备批量关闭...");

                                // 2. 遍历每一条订单，逐个关闭
                                for (Order order : orders) {
                                    // 使用 MyBatis-Plus 的 UpdateWrapper
                                    UpdateWrapper<Order> update = new UpdateWrapper<>();
                                    update.set("status", 2);
                                    update.eq("id", order.getId());
                                    update.eq("status", 0); // 【核心】只有当前是0时才允许改成2
                                    int rows = orderMapper.update(null, update);

                                    // 3. 只有当前这条订单更新成功，才回补 1 个库存
                                    if (rows > 0) {
                                        try (Jedis jedis = jedisPool.getResource()) {
                                            jedis.incr("stock:" + goodsId);
                                            System.out.println("    -> 📦 订单 " + order.getId() + " 已关闭，库存回补 +1");
                                        }
                                    }
                                }
                            } else {
                                // order 为 null 有两种情况：
                                // 1. 用户已经支付了 (status=1)
                                // 2. 订单已经关过了 (status=2) - 幂等性保障
                                System.out.println("    -> 订单已支付或不存在，无需处理。");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 如果处理失败（例如数据库挂了），返回 RECONSUME_LATER 让 MQ 稍后重试
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });

            // 5. 启动消费者
            consumer.start();
            System.out.println(">>> RocketMQ 订单消费者已启动，正在监听 MySQL 写入请求...");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}