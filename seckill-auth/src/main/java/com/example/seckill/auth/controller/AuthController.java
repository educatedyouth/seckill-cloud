package com.example.seckill.auth.controller;

import com.example.seckill.auth.feign.UserFeignClient;
import com.example.seckill.auth.vo.LoginVo;
import com.example.seckill.common.entity.User;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private PasswordEncoder passwordEncoder; // 注入 SecurityConfig 里配的 BCrypt

    @Autowired
    private JwtUtil jwtUtil; // 注入我们写的工具类

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginVo loginVo) {
        // 1. 参数校验
        if (loginVo.getUsername() == null || loginVo.getPassword() == null) {
            return Result.error("用户名或密码不能为空");
        }

        // 2. 远程调用 User 服务，查询用户信息
        Result<User> userResult = userFeignClient.getUserByUsername(loginVo.getUsername());

        // 检查 Feign 调用是否成功以及用户是否存在
        if (userResult == null || userResult.getCode() != 200 || userResult.getData() == null) {
            return Result.error("用户不存在");
        }

        User user = userResult.getData();

        // 3. 【核心】校验密码
        // passwordEncoder.matches(用户输入的明文, 数据库里的密文)
        if (!passwordEncoder.matches(loginVo.getPassword(), user.getPassword())) {
            return Result.error("密码错误");
        }

        // 4. 生成 JWT 令牌
        String token = jwtUtil.createToken(Long.valueOf(user.getId()));

        // 5. 返回 Token 给前端
        return Result.success(token);
    }
}