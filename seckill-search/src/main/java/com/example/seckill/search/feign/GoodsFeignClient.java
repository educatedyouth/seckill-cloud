package com.example.seckill.search.feign;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.vo.GoodsDetailVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务远程客户端
 * name = "seckill-goods": 指定调用哪个微服务
 */
@FeignClient(name = "seckill-goods")
public interface GoodsFeignClient {

    /**
     * 调用商品详情接口
     * 复用已有的接口：/goods/detail/{spuId}
     * 注意：这里需要引入 seckill-goods 的依赖或者把 VO 搬到 common 包。
     * 架构师提示：由于 GoodsDetailVO 目前在 seckill-goods 模块里，
     * 为了解耦，正规做法是把 GoodsDetailVO 移动到 seckill-common 模块。
     * * 【本次操作指令】：请先暂停，将 seckill-goods 中的 GoodsDetailVO 移动到 seckill-common 中，
     * 否则 seckill-search 引用不到它。
     */
    @GetMapping("/goods/detail/{spuId}")
    Result<GoodsDetailVO> getGoodsDetail(@PathVariable("spuId") Long spuId);
}