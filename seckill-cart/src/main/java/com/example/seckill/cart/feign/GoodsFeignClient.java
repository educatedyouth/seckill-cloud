package com.example.seckill.cart.feign;

import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "seckill-goods")
public interface GoodsFeignClient {

    @RequestMapping("/sku/info")
    Result<SkuInfo> getSkuInfo(@RequestParam("skuId") Long skuId);

    @RequestMapping("/sku/saleAttr/{skuId}")
    Result<List<String>> getSkuSaleAttrValues(@PathVariable("skuId") Long skuId);
}