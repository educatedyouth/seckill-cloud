package com.example.seckill.goods.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.goods.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}