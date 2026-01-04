package com.example.seckill.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckill.common.entity.SkuInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SkuInfoMapper extends BaseMapper<SkuInfo> {
    // 继承 BaseMapper 后，你自动拥有了 insert, selectById 等 CRUD 方法
    /**
     * 数据库原子扣减库存 (普通交易专用)
     * UPDATE sku_info SET stock = stock - count WHERE id = skuId AND stock >= count
     * @param skuId 商品SKU ID
     * @param count 扣减数量
     * @return 影响行数 (1=成功, 0=失败/库存不足)
     */
    @Update("UPDATE pms_sku_info SET stock = stock - #{count} WHERE sku_id = #{skuId} AND stock >= #{count}")
    int reduceStock(@Param("skuId") Long skuId, @Param("count") Integer count);
}