package com.example.seckill.cart.service;

import com.example.seckill.common.vo.CartItem;
import java.util.List;

public interface CartService {

    /**
     * 添加商品到购物车
     * @param userId 用户ID
     * @param skuId 商品ID
     * @param count 数量
     * @return 添加后的购物项
     */
    CartItem addToCart(Long userId, Long skuId, Integer count);

    /**
     * 获取用户购物车列表
     * @param userId 用户ID
     * @return 列表
     */
    List<CartItem> getCartList(Long userId);

    /**
     * 删除购物车中的商品
     * @param userId 用户ID
     * @param skuId 商品ID
     */
    void deleteItem(Long userId, Long skuId);
    /**
     * 合并购物车 (将离线购物车数据合并到在线购物车)
     * @param userId 用户ID
     * @param cartItems 离线购物车项列表
     */
    void mergeCart(Long userId, List<CartItem> cartItems);
}