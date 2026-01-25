package com.example.seckill.order.controller;

import com.example.seckill.common.entity.User;
import com.example.seckill.common.result.Result;
import com.example.seckill.order.feign.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order/test")
public class OrderTestController {

    @Autowired
    private UserFeignClient userFeignClient; // 注入刚才写的电话本

    @GetMapping("/user/{id}")
    public Result<User> testGetUser(@PathVariable Integer id) {
        System.out.println(">>> 订单服务准备呼叫用户服务，查询 ID: " + id);

        // 这一步就是远程调用！像调用本地方法一样简单
        Result<User> userResult = userFeignClient.getUser(id);

        return userResult;
    }
    @GetMapping("/whoami")
    public String whoAmI() {
        Long userId = com.example.seckill.common.context.UserContext.getUserId();
        return "当前登录用户ID是: " + userId;
    }
}