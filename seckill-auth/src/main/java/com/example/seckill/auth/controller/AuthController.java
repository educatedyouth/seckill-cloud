package com.example.seckill.auth.controller;

import com.example.seckill.auth.feign.UserFeignClient;
import com.example.seckill.common.vo.LoginVo;
import com.example.seckill.common.entity.User;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.utils.JwtUtil;
import com.example.seckill.common.vo.RegisterVo;
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
    /**
     * 【新增】注册接口
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterVo registerVo) {
        // 1. 基础校验
        if (registerVo.getUsername() == null || registerVo.getPassword() == null) {
            return Result.error("用户名或密码不能为空");
        }

        // 2. 密码加密 (核心安全步骤)
        // 注意：我们不能把明文密码存入数据库，必须在这里加密后再发给 User 服务
        String rawPassword = registerVo.getPassword();
        String encodedPassword = passwordEncoder.encode(rawPassword);
        registerVo.setPassword(encodedPassword);

        // 3. 远程调用 User 服务写入数据
        return userFeignClient.register(registerVo);
    }
    /**
     * 【新增】退出登录接口
     * 目前是无状态 JWT，前端清除即可。
     * 但后端必须预留接口，用于后续扩展（如：将 Token 加入 Redis 黑名单，强制失效）。
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        // TODO: 后续在这里获取 Header 中的 Token，解析过期时间，存入 Redis 黑名单
        // log.info("用户发起退出请求...");
        return Result.success("退出成功");
    }
}