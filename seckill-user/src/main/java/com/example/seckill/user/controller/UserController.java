package com.example.seckill.user.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.entity.User;
import com.example.seckill.common.vo.RegisterVo;
import com.example.seckill.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    // 以下为非内部接口
    @GetMapping("/get/{phoneNumber}")
    public Result<User> getUserByPhone(@PathVariable String phoneNumber) {
        // 暂时 mock 一个数据，不查数据库，先跑通流程
        User user = userService.getByPhone(phoneNumber);
        return Result.success(user);
    }
    /**
     * 用户注册接口
     * POST /user/register
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterVo registerVo) {
        // 简单的参数判空
        if (registerVo.getUsername() == null || registerVo.getPassword() == null) {
            return Result.error("用户名或密码不能为空");
        }

        try {
            User user = userService.register(registerVo);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}