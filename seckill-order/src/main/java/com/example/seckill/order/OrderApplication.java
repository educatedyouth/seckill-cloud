package com.example.seckill.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient // 开启服务发现
@MapperScan("com.example.seckill.order.mapper") // 扫描 Mapper 接口
@EnableFeignClients // 【新增】开启远程调用功能
// 【关键修复】扫描 common 模块下的拦截器和配置
@ComponentScan(basePackages = {"com.example.seckill"})
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}