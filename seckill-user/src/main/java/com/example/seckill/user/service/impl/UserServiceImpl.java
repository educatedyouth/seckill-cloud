package com.example.seckill.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.common.entity.User;
import com.example.seckill.common.vo.RegisterVo;
import com.example.seckill.user.mapper.UserMapper;
import com.example.seckill.user.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * ServiceImpl<Mapper, Entity>:
 * 这是 MyBatis-Plus 的神器，帮你自动把 Mapper 注入进来，并实现了 IService 的所有基础方法。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    // 实例化一个加密器
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User getByUsername(String username) {
        // 业务逻辑层：这里可以加缓存、日志、校验等
        // 目前只负责查库
        return this.getOne(new QueryWrapper<User>().eq("username", username));
    }
    @Override
    public User getByPhone(String phoneNumber) {
        // 业务逻辑层：这里可以加缓存、日志、校验等
        // 目前只负责查库
        return this.getOne(new QueryWrapper<User>().eq("phone", phoneNumber));
    }

    @Override
    public User register(RegisterVo registerVo) {
        // 1. 校验用户名是否已存在
        User existUser = this.getByUsername(registerVo.getUsername());
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
            // 生产环境建议用自定义异常，这里先简单抛RuntimeException，会被全局异常捕获或直接报错
        }

        // 2. 准备新用户对象
        User user = new User();
        user.setUsername(registerVo.getUsername());
        user.setPhone(registerVo.getPhone());

        // 【核心】密码加密存储！
        // 这里的 encode 方法会自动生成随机盐，每次结果都不一样，安全性极高
        String encodePwd = passwordEncoder.encode(registerVo.getPassword());
        user.setPassword(encodePwd);

        // 3. 写入数据库
        this.save(user);

        // 4. 返回结果前，把密码擦除，防止泄露
        user.setPassword(null);
        return user;
    }
}