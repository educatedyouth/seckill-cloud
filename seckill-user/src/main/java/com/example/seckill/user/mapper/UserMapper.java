package com.example.seckill.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.common.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后，你自动拥有了 insert, selectById 等 CRUD 方法
    // 不需要写一行 SQL
}