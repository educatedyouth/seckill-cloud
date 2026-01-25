package com.example.seckill.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.goods.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    // 继承 BaseMapper 后，你自动拥有了 insert, selectById 等 CRUD 方法
    // 不需要写一行 SQL
}