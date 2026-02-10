package com.example.seckill.order.config;

import com.example.seckill.order.listener.SeckillTransactionListener;
import lombok.Data;
import org.apache.rocketmq.client.producer.TransactionListener;
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
    // 从 application.yml 读取 NameServer 地址 (10.201.115.99:9876)
    @Value("${app.rocketmq.namesrv-addr}")
    private String namesrvAddr;

    // 从 application.yml 读取生产者组名
    @Value("${app.rocketmq.producer-group}")
    private String producerGroup;

}