package com.example.seckill.cart.controller;

import com.example.seckill.cart.service.CartService;
import com.example.seckill.common.vo.CartItem;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 添加商品到购物车
     * 前端传递: skuId, count
     * Header需携带 Token
     */
    @PostMapping("/add")
    public Result<CartItem> addToCart(@RequestParam("skuId") Long skuId,
                                      @RequestParam("count") Integer count) {
        // 从 ThreadLocal 获取当前登录用户 ID (依赖 GateWay -> Interceptor 链路)
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 这里的处理是为了兼容后续的“离线购物车”，暂时抛错，前端需引导登录
            // 实际生产中，未登录会存 cookie，这里简化为必须登录
            return Result.error("请先登录");
        }

        CartItem item = cartService.addToCart(userId, skuId, count);
        return Result.success(item);
    }

    /**
     * 获取购物车列表
     */
    @GetMapping("/list")
    public Result<List<CartItem>> getCartList() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        List<CartItem> list = cartService.getCartList(userId);
        return Result.success(list);
    }

    /**
     * 删除购物车商品
     */
    @PostMapping("/delete")
    public Result<String> deleteItem(@RequestParam("skuId") Long skuId) {
        Long userId = UserContext.getUserId();
        cartService.deleteItem(userId, skuId);
        return Result.success("删除成功");
    }

    /**
     * 合并购物车
     * 触发时机：用户登录成功后
     */
    @PostMapping("/merge")
    public Result<String> mergeCart(@RequestBody List<CartItem> cartItems) {
        Long userId = UserContext.getUserId();
        cartService.mergeCart(userId, cartItems);
        return Result.success("合并成功");
    }
}