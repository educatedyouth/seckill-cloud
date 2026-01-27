package com.example.seckill.order.feign;

import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.vo.CartItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "seckill-goods")
public interface GoodsFeignClient {

    @RequestMapping("/sku/info")
    Result<SkuInfo> getSkuInfo(@RequestParam("skuId") Long skuId);

    // 核心：数据库扣减库存
    @PostMapping("/sku/reduce/db")
    Result<String> reduceStockDB(@RequestParam("skuId") Long skuId,
                                 @RequestParam("count") Integer count);

    // 批量数据库扣减库存
    @PostMapping("/sku/reduce/dbBatch")
    Result<String> reduceStockDBBatch(@RequestBody List<CartItem>CartItems);
}