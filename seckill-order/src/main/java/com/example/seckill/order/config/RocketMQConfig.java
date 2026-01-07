package com.example.seckill.order.config;

import com.example.seckill.order.listener.SeckillTransactionListener;
import lombok.Data;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 配置类
 * 作用：初始化生产者实例，并将其注册为 Spring Bean
 */
@Data // 【重要】自动生成 Getter/Setter，否则 service 无法调用 getOrderTopic()
@Configuration // 标识这是一个 Spring 配置类，相当于 XML 配置文件
public class RocketMQConfig {
    // 直接读取 yaml 中的 app.rocketmq.topic
    @Value("${app.rocketmq.topic}")
    private String orderTopic;
    // 从 application.yml 读取 NameServer 地址 (100.113.176.73:9876)
    @Value("${app.rocketmq.namesrv-addr}")
    private String namesrvAddr;

    // 从 application.yml 读取生产者组名
    @Value("${app.rocketmq.producer-group}")
    private String producerGroup;

    /**
     * 定义一个 Bean：TransactionMQProducer
     * Spring 启动时会执行这个方法，把返回的对象放入容器。
     * * @param transactionListener Spring 会自动找到 SeckillTransactionListener 组件并注入到这里
     * initMethod = "start": Bean 创建完成后，自动调用 producer.start() 启动 MQ
     * destroyMethod = "shutdown": Bean 销毁时(停服)，自动调用 producer.shutdown() 释放资源
     */
    //@Bean(initMethod = "start", destroyMethod = "shutdown")
    public TransactionMQProducer transactionMQProducer(SeckillTransactionListener transactionListener) {
        // 1. 实例化事务生产者，指定组名
        TransactionMQProducer producer = new TransactionMQProducer(producerGroup);

        // 2. 设置 NameServer 地址
        producer.setNamesrvAddr(namesrvAddr);

        // 3. 设置自定义线程池
        // 作用：当 Broker 主动发起“事务回查”时，生产者会使用这个线程池里的线程去执行 checkLocalTransaction 方法
        ExecutorService executorService = new ThreadPoolExecutor(
                2, // 核心线程数
                5, // 最大线程数
                100, TimeUnit.SECONDS, // 空闲线程存活时间
                new ArrayBlockingQueue<>(2000), // 任务队列，如果回查请求堆积超过 2000 会拒绝
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("client-transaction-msg-check-thread"); // 给线程起个名，方便查日志
                    return thread;
                }
        );
        producer.setExecutorService(executorService);

        // 4. 【关键绑定】将我们写的事务监听逻辑，绑定到这个生产者上
        // 这样当生产者发送事务消息时，RocketMQ 才知道去哪里执行本地事务
        producer.setTransactionListener(transactionListener);

        System.out.println(">>> RocketMQ 事务生产者初始化完成，NameServer: " + namesrvAddr);
        return producer;
    }
}