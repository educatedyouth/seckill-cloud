package com.example.seckill.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.common.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    // 继承 BaseMapper 后，你自动拥有了 insert, selectById 等 CRUD 方法
    // 不需要写一行 SQL
}