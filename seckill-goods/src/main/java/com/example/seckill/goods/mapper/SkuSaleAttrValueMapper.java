package com.example.seckill.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.goods.entity.SkuSaleAttrValue; // 引用刚才创建的 Entity
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SkuSaleAttrValueMapper extends BaseMapper<SkuSaleAttrValue> {
}