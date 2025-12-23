package com.example.seckill.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Value("${app.redis.host}")
    private String host;

    @Value("${app.redis.port}")
    private int port;

    @Value("${app.redis.password}")
    private String password;

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);
        poolConfig.setMaxIdle(50);
        poolConfig.setMinIdle(10);
        poolConfig.setMaxWaitMillis(3000);
        // 【新增这一行】关闭 JMX 注册，解决启动报错
        poolConfig.setJmxEnabled(false);
        // 如果密码为空，传 null 否则传真实密码
        String pwd = (password == null || password.trim().isEmpty()) ? null : password;

        System.out.println(">>> Redis 连接池初始化: " + host + ":" + port);
        return new JedisPool(poolConfig, host, port, 2000, pwd);
    }
}