package com.example.seckill.order.feign;

import com.example.seckill.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 支付服务远程调用客户端
 * 注意：name = "seckill-order" 是因为我们将 MockController 写在了 order 服务里方便测试。
 * 生产环境这里通常是 "seckill-payment" 或第三方支付网关的 Wrapper 服务。
 */
@FeignClient(name = "seckill-order")
public interface PayFeignClient {

    /**
     * 远程查询订单支付状态
     * @return true=已支付, false=未支付
     */
    @GetMapping("/pay/mock/check/{orderId}")
    Result<Boolean> checkPayStatus(@PathVariable("orderId") Long orderId);
}