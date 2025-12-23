package com.example.seckill.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckill.common.entity.User;

/**
 * 用户业务接口
 * 继承 IService 是为了使用 MyBatis-Plus 提供的基础 CRUD 方法 (getById, save, update等)
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户（包含密码密文）
     * 专门给 Auth 服务用的
     */
    User getByUsername(String username);
}