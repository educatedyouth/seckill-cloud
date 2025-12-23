package com.example.seckill.user.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/get/{id}")
    public Result<User> getUser(@PathVariable Integer id) {
        // 暂时 mock 一个数据，不查数据库，先跑通流程
        User user = new User();
        user.setId(id);
        user.setUsername("TestUser_" + id);
        user.setPhone("13800138000");
        return Result.success(user);
    }
}