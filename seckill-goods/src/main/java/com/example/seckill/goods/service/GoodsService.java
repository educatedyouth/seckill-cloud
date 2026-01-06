package com.example.seckill.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckill.common.vo.CartItem;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.common.entity.SpuInfo;
import com.example.seckill.common.vo.GoodsDetailVO;

import java.util.List;

// seckill-goods/src/main/java/com/example/seckill/goods/service/GoodsService.java
public interface GoodsService extends IService<SpuInfo> {
    /**
     * 商品上架（复杂事务保存）
     * @param spuSaveDTO 前端传来的大对象
     */
    void saveGoods(SpuSaveDTO spuSaveDTO);

    /**
     * 商品详情页
     * @param spuId 前端传递的Id
     */
    // seckill-goods/src/main/java/com/example/seckill/goods/service/GoodsService.java
    GoodsDetailVO getGoodsDetail(Long spuId);

    void updateGoods(SpuSaveDTO dto);
    void updateStatus(Long spuId, Integer status);
    /**
     * 级联删除商品 (SPU + SKU + Images + Attrs)
     */
    void removeGoods(Long spuId);

    void deleteOneBySpuID(Long spuID);

    /**
     * 【新增】全量同步商品到 ES
     * 逻辑：查询所有 SPU ID，循环发送 MQ 上架消息
     */
    void syncAllGoods();

    /**
     * 数据库直接扣减库存（受 Seata 分布式事务管理）
     * @param skuId SKU ID
     * @param count 数量
     * @return 是否成功
     */
    boolean reduceStockDB(Long skuId, Integer count);

    boolean reduceStockDBBatch(List<CartItem>CartItems);
}