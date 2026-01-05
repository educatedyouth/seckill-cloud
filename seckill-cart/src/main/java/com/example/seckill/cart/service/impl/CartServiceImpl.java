package com.example.seckill.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.example.seckill.cart.feign.GoodsFeignClient;
import com.example.seckill.cart.service.CartService;
import com.example.seckill.common.vo.CartItem;
import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private GoodsFeignClient goodsFeignClient;

    private static final String CART_PREFIX = "cart:";

    @Override
    public CartItem addToCart(Long userId, Long skuId, Integer count) {
        // 1. 【关键改进】无论 Redis 有没有，先远程查最新的商品信息（获取最新库存）
        Result<SkuInfo> result = goodsFeignClient.getSkuInfo(skuId);
        if (result == null || result.getData() == null) {
            throw new RuntimeException("商品信息不存在: " + skuId);
        }
        SkuInfo skuInfo = result.getData();

        // 2. 获取购物车操作对象
        BoundHashOperations<String, Object, Object> cartOps = getCartOps(userId);
        String redisValue = (String) cartOps.get(skuId.toString());

        CartItem cartItem;
        if (redisValue != null) {
            // 3. 购物车已存在：反序列化 -> 累加数量
            cartItem = JSON.parseObject(redisValue, CartItem.class);
            cartItem.setCount(cartItem.getCount() + count);
            // 如果价格变动，这里也可以顺便更新一下价格（可选策略）
            cartItem.setPrice(skuInfo.getPrice());
            cartItem.setStock(skuInfo.getStock()); // 【新增】写入 Redis 时记录当时的库存
        } else {
            // 4. 新增：构建对象
            cartItem = new CartItem();
            cartItem.setSkuId(skuInfo.getSkuId());
            cartItem.setSpuId(skuInfo.getSpuId());
            cartItem.setTitle(skuInfo.getSkuTitle());
            cartItem.setImage(skuInfo.getSkuDefaultImg());
            cartItem.setPrice(skuInfo.getPrice());
            cartItem.setCount(count);
            cartItem.setStock(skuInfo.getStock()); // 【新增】写入 Redis 时记录当时的库存
        }
        // 【新增】远程查询 SKU 规格属性
        Result<List<String>> attrResult = goodsFeignClient.getSkuSaleAttrValues(skuId);
        if (attrResult != null && attrResult.getData() != null) {
            cartItem.setSkuAttr(attrResult.getData());
        } else {
            cartItem.setSkuAttr(new java.util.ArrayList<>()); // 防止 null
        }
        // 5. 【核心防超卖】二次校验：(购物车现有 + 本次新增) 是否超过当前真实库存
        if (cartItem.getCount() > skuInfo.getStock()) {
            // 抛出异常，前端 request.js 会捕获并弹出 Message.error
            throw new RuntimeException("库存不足，当前仅剩 " + skuInfo.getStock() + " 件");
        }

        // 6. 校验通过，写入 Redis
        cartOps.put(skuId.toString(), JSON.toJSONString(cartItem));
        return cartItem;
    }

    @Override
    public List<CartItem> getCartList(Long userId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps(userId);
        List<Object> values = cartOps.values();

        if (values != null) {
            return values.stream()
                    .map(obj -> JSON.parseObject((String) obj, CartItem.class))
                    .collect(Collectors.toList());
        }
        return null;
    }

    private BoundHashOperations<String, Object, Object> getCartOps(Long userId) {
        String cartKey = CART_PREFIX + userId;
        return redisTemplate.boundHashOps(cartKey);
    }

    @Override
    public void deleteItem(Long userId, Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps(userId);
        // Redis Hash Delete 命令
        cartOps.delete(skuId.toString());
    }
    @Override
    public void mergeCart(Long userId, List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }
        // 遍历离线购物车的所有商品，复用 addToCart 逻辑
        // 这样可以自动处理：库存校验、价格更新、数量累加
        for (CartItem item : cartItems) {
            try {
                this.addToCart(userId, item.getSkuId(), item.getCount());
            } catch (Exception e) {
                // 如果某个商品合并失败（比如没库存了），记录日志，但不阻断其他商品合并
                log.error("合并购物车商品失败: skuId={}, msg={}", item.getSkuId(), e.getMessage());
            }
        }
    }
}