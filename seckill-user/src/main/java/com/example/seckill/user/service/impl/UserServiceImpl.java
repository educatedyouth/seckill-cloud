package com.example.seckill.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.common.entity.User;
import com.example.seckill.user.mapper.UserMapper;
import com.example.seckill.user.service.UserService;
import org.springframework.stereotype.Service;

/**
 * ServiceImpl<Mapper, Entity>:
 * 这是 MyBatis-Plus 的神器，帮你自动把 Mapper 注入进来，并实现了 IService 的所有基础方法。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User getByUsername(String username) {
        // 业务逻辑层：这里可以加缓存、日志、校验等
        // 目前只负责查库
        return this.getOne(new QueryWrapper<User>().eq("username", username));
    }
}