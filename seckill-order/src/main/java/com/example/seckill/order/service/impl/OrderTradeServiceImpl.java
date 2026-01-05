package com.example.seckill.order.service.impl;

import com.example.seckill.common.vo.CartItem;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.entity.OrderTrade; // 使用新实体
import com.example.seckill.common.result.Result;
import com.example.seckill.order.feign.CartFeignClient;
import com.example.seckill.order.feign.GoodsFeignClient;
import com.example.seckill.order.mapper.OrderTradeMapper; // 使用新 Mapper
import com.example.seckill.order.service.OrderTradeService;
import com.example.seckill.order.vo.OrderSubmitVo;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class OrderTradeServiceImpl implements OrderTradeService {

    @Autowired
    private OrderTradeMapper orderTradeMapper; // 注入新 Mapper
    @Autowired
    private CartFeignClient cartFeignClient;
    @Autowired
    private GoodsFeignClient goodsFeignClient;

    @Override
    @GlobalTransactional(name = "create-order-tx", rollbackFor = Exception.class)
    public Result<String> createOrder(OrderSubmitVo submitVo) {
        // 1. 获取当前用户 ID (Long 类型)
        Long userId = UserContext.getUserId();

        // 2. 远程调用购物车服务
        Result<List<CartItem>> cartRes = cartFeignClient.getCartList();
        if (cartRes == null || cartRes.getData() == null || cartRes.getData().isEmpty()) {
            return Result.error("购物车为空");
        }
        List<CartItem> cartItems = cartRes.getData();

        // 3. 循环处理每一个购物项 (拆单逻辑)
        List<String> orderIds = new ArrayList<>();

        for (CartItem item : cartItems) {
            // --- A. 远程扣减库存 (RPC -> Goods DB) ---
            Result<String> reduceRes = goodsFeignClient.reduceStockDB(item.getSkuId(), item.getCount());
            if (reduceRes.getCode() != 200) {
                log.error("商品库存扣减失败: {}", item.getTitle());
                throw new RuntimeException("库存不足: " + item.getTitle());
            }

            // --- B. 创建全新的 OrderTrade 对象 ---
            OrderTrade order = new OrderTrade();
            order.setUserId(userId);
            order.setSkuId(item.getSkuId());
            order.setCount(item.getCount());

            // 金额计算
            BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(new BigDecimal(item.getCount()));
            order.setTotalAmount(amount);
            order.setPayAmount(amount); // 暂时无优惠
            order.setFreightAmount(BigDecimal.ZERO);

            // 状态设置
            order.setStatus(0); // 待支付
            order.setOrderType(0); // 普通订单
            order.setCreateTime(new Date());
            order.setModifyTime(new Date());

            // 收货信息 (这里暂时 Mock，后续从 UserAddr 获取)
            order.setReceiverName("测试用户");
            order.setReceiverPhone("13800000000");
            order.setReceiverDetailAddress("测试地址");
            order.setNote(submitVo.getRemark());

            // --- C. 插入数据库 ---
            // MyBatisPlus 会自动生成雪花算法 ID (Long) 并填入 order.id
            orderTradeMapper.insert(order);

            log.info("商品[{}]生成订单成功, 订单ID: {}", item.getSkuId(), order.getId());
            orderIds.add(String.valueOf(order.getId()));
        }

        // 4. 清理购物车
        for (CartItem item : cartItems) {
            cartFeignClient.deleteItem(item.getSkuId());
        }

        return Result.success(String.join(",", orderIds));
    }
}