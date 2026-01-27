package com.example.seckill.order.feign;

import com.example.seckill.common.vo.CartItem;
import com.example.seckill.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "seckill-cart")
public interface CartFeignClient {

    // 获取当前用户勾选的购物车商品 (需自己在 Cart 服务实现，或暂时获取全部)
    // 为了简化，我们暂时复用 getCartList，实际生产应有 /cart/checked/list
    @GetMapping("/cart/list")
    Result<List<CartItem>> getCartList();

    // 下单成功后删除购物车商品
    @PostMapping("/cart/delete")
    Result<String> deleteItem(@RequestParam("skuId") Long skuId);
}