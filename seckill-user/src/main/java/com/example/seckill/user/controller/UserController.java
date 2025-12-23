package com.example.seckill.user.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.entity.User;
import com.example.seckill.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService; // 记得注入 Service

    /**
     * 【内部接口】供 Auth 服务调用，根据用户名获取用户信息（包含密码密文）
     */
    @GetMapping("/internal/get/{username}")
    public Result<User> getUserByUsername(@PathVariable String username) {
        User user = userService.getByUsername(username);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }
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