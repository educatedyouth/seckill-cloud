package com.example.seckill.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.common.entity.SkuImages; // 引用刚才创建的 Entity
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkuImagesMapper extends BaseMapper<SkuImages> {
}