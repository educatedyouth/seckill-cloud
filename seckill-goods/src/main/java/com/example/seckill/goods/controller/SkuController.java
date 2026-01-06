package com.example.seckill.goods.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.vo.CartItem;
import com.example.seckill.goods.service.GoodsService;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU 核心操作接口
 */
@RestController
@RequestMapping("/sku")
public class SkuController {

    @Autowired
    private GoodsService goodsService;

    /**
     * 数据库扣减库存 (供订单服务 Feign 调用)
     */
    @PostMapping("/reduce/db")
    public Result<String> reduceStockDB(@RequestParam("skuId") Long skuId,
                                        @RequestParam("count") Integer count) {
        boolean success = goodsService.reduceStockDB(skuId, count);
        if (success) {
            return Result.success("库存扣减成功");
        } else {
            return Result.error("库存扣减失败");
        }
    }
    /**
     * 数据库批量扣减库存 (供订单服务 Feign 调用)
     */
    @PostMapping("/reduce/dbBatch")
    Result<String> reduceStockDBBatch(@Param("items") List<CartItem>CartItems){
        boolean success = goodsService.reduceStockDBBatch(CartItems);
        if (success) {
            return Result.success("库存扣减成功");
        } else {
            return Result.error("库存扣减失败");
        }
    }

    @Autowired
    private com.example.seckill.goods.mapper.SkuInfoMapper skuInfoMapper;

    /**
     * 获取 SKU 详情 (供购物车/订单服务远程调用)
     */
    @RequestMapping("/info")
    public Result<com.example.seckill.common.entity.SkuInfo> getSkuInfo(@RequestParam("skuId") Long skuId) {
        com.example.seckill.common.entity.SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if (skuInfo == null) {
            return Result.error("商品不存在");
        }
        return Result.success(skuInfo);
    }

    // 注入属性 Mapper
    @Autowired
    private com.example.seckill.goods.mapper.SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    /**
     * 获取 SKU 的销售属性文本列表
     * 例如：["颜色: 红色", "内存: 128G"]
     */
    @RequestMapping("/saleAttr/{skuId}")
    public Result<List<String>> getSkuSaleAttrValues(@PathVariable("skuId") Long skuId) {
        // 查库：select * from pms_sku_sale_attr_value where sku_id = ?
        List<com.example.seckill.common.entity.SkuSaleAttrValue> attrValues = skuSaleAttrValueMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.example.seckill.common.entity.SkuSaleAttrValue>()
                        .eq("sku_id", skuId)
        );

        // 转换：将实体列表转换为 "Key: Value" 格式的字符串列表
        List<String> attrList = attrValues.stream()
                .map(attr -> attr.getAttrName() + ": " + attr.getAttrValue())
                .collect(java.util.stream.Collectors.toList());

        return Result.success(attrList);
    }
}