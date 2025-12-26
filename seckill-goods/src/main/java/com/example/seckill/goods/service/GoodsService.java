package com.example.seckill.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.goods.entity.SpuInfo;

// seckill-goods/src/main/java/com/example/seckill/goods/service/GoodsService.java
public interface GoodsService extends IService<SpuInfo> {
    /**
     * 商品上架（复杂事务保存）
     * @param spuSaveDTO 前端传来的大对象
     */
    void saveGoods(SpuSaveDTO spuSaveDTO);
}