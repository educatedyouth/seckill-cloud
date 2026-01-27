package com.example.seckill.goods;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient // 开启服务发现
@MapperScan("com.example.seckill.goods.mapper") // 扫描 Mapper 接口
// 【关键修复】扫描 common 模块下的拦截器和配置
@ComponentScan(basePackages = {"com.example.seckill"})
public class GoodsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoodsApplication.class, args);
        System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("123456"));
    }
}