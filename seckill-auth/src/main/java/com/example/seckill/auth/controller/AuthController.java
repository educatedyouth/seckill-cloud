package com.example.seckill.auth.controller;

import com.example.seckill.auth.feign.UserFeignClient;
import com.example.seckill.common.utils.RedisUtil;
import com.example.seckill.common.vo.LoginVo;
import com.example.seckill.common.entity.User;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.utils.JwtUtil;
import com.example.seckill.common.vo.RegisterVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private PasswordEncoder passwordEncoder; // 注入 SecurityConfig 里配的 BCrypt

    @Autowired
    private JwtUtil jwtUtil; // 注入我们写的工具类

    @Autowired
    private RedisUtil redisUtil; // 注入工具类

    @PostMapping("/login")
    // 1. 修改泛型，从 String 改为 Map<String, Object>
    public Result<Map<String, Object>> login(@RequestBody LoginVo loginVo) {
        // 1. 参数校验
        if (loginVo.getUsername() == null || loginVo.getPassword() == null) {
            return Result.error("用户名或密码不能为空");
        }

        // 2. 远程调用 User 服务
        Result<User> userResult = userFeignClient.getUserByUsername(loginVo.getUsername());

        if (userResult == null || userResult.getCode() != 200 || userResult.getData() == null) {
            return Result.error("用户不存在");
        }

        User user = userResult.getData();

        // 3. 校验密码
        if (!passwordEncoder.matches(loginVo.getPassword(), user.getPassword())) {
            return Result.error("密码错误");
        }

        // 4. 生成 Token
        String token = jwtUtil.createToken(Long.valueOf(user.getId()));

        // 5. 【核心修改】构建 Map 返回给前端
        // 前端 store/user.js 需要 data.token 和 data.userId
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("token", token);
        map.put("userId", user.getId());

        return Result.success(map);
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
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader("Authorization") String token) {
        // 1. 解析 Token (去掉 "Bearer " 前缀)
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 2. 将 Token 加入黑名单
        // 这里的过期时间应该与 JWT 的剩余有效期一致，或者简单点设置一个固定值（如 24小时）
        // Key 格式建议：blacklist:token:{token_string}
        redisUtil.set("blacklist:token:" + token, "1", 86400); // 暂定 24 小时过期

        return Result.success("退出成功");
    }
}