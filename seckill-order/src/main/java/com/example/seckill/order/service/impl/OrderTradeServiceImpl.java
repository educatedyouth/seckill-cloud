package com.example.seckill.order.service.impl;

import com.example.seckill.common.vo.CartItem;
import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.entity.OrderTrade;
import com.example.seckill.common.entity.UserAddr; // 引入 UserAddr
import com.example.seckill.common.result.Result;
import com.example.seckill.order.feign.CartFeignClient;
import com.example.seckill.order.feign.GoodsFeignClient;
import com.example.seckill.order.feign.UserFeignClient; // 引入 UserFeignClient
import com.example.seckill.order.mapper.OrderTradeMapper;
import com.example.seckill.order.service.OrderTradeService;
import com.example.seckill.order.vo.OrderSubmitVo;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderTradeServiceImpl implements OrderTradeService {

    @Autowired
    private OrderTradeMapper orderTradeMapper;
    @Autowired
    private CartFeignClient cartFeignClient;
    @Autowired
    private GoodsFeignClient goodsFeignClient;

    // 【新增注入】
    @Autowired
    private UserFeignClient userFeignClient;

    @Override
    @GlobalTransactional(name = "create-order-tx", rollbackFor = Exception.class)
    public Result<String> createOrder(OrderSubmitVo submitVo) {
        // 1. 获取当前用户 ID
        Long userId = UserContext.getUserId();

        // 2. 校验基本参数
        if (submitVo.getAddrId() == null) {
            return Result.error("请选择收货地址");
        }
        // 【新增】校验是否勾选了商品
        if (submitVo.getSkuIds() == null || submitVo.getSkuIds().isEmpty()) {
            return Result.error("请至少选择一件商品进行结算");
        }

        // --- A. 远程查询收货地址 ---
        Result<UserAddr> addrRes = userFeignClient.getUserAddrInfo(submitVo.getAddrId());
        if (addrRes == null || addrRes.getData() == null) {
            return Result.error("收货地址信息无效");
        }
        UserAddr userAddr = addrRes.getData();
        if (!userId.equals(userAddr.getUserId())) {
            return Result.error("非法的收货地址");
        }

        // 3. 远程调用购物车服务 (获取全部)
        Result<List<CartItem>> cartRes = cartFeignClient.getCartList();
        if (cartRes == null || cartRes.getData() == null || cartRes.getData().isEmpty()) {
            return Result.error("购物车为空");
        }
        List<CartItem> allCartItems = cartRes.getData();

        // 【核心改造】只筛选出用户勾选的商品
        List<CartItem> selectedItems = allCartItems.stream()
                .filter(item -> submitVo.getSkuIds().contains(item.getSkuId()))
                .collect(Collectors.toList());

        if (selectedItems.isEmpty()) {
            return Result.error("您勾选的商品已不在购物车中，请刷新");
        }
        Result<String> reduceRes = goodsFeignClient.reduceStockDBBatch(selectedItems);
        if (reduceRes.getCode() != 200) {
            log.error("商品库存扣减失败，检查是否库存不走");
            throw new RuntimeException("库存不足");
        }
        // 4. 循环处理每一个【选中的】购物项
        List<String> orderIds = new ArrayList<>();
        for (CartItem item : selectedItems) { // 遍历 selectedItems 而不是 allCartItems
            // --- B. 远程扣减库存 ---
            // Result<String> reduceRes = goodsFeignClient.reduceStockDB(item.getSkuId(), item.getCount());
            // --- C. 创建订单对象 ---
            OrderTrade order = new OrderTrade();
            order.setUserId(userId);
            order.setSkuId(item.getSkuId());
            order.setCount(item.getCount());

            BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(new BigDecimal(item.getCount()));
            order.setTotalAmount(amount);
            order.setPayAmount(amount);
            order.setFreightAmount(BigDecimal.ZERO);

            order.setStatus(0);
            order.setOrderType(0);
            order.setCreateTime(new Date());
            order.setModifyTime(new Date());

            // 填入真实收货信息
            order.setReceiverName(userAddr.getReceiverName());
            order.setReceiverPhone(userAddr.getReceiverPhone());
            String fullAddress = (userAddr.getProvince() == null ? "" : userAddr.getProvince()) + " " +
                    (userAddr.getCity() == null ? "" : userAddr.getCity()) + " " +
                    (userAddr.getArea() == null ? "" : userAddr.getArea()) + " " + // 注意这里用 Area
                    userAddr.getDetailAddr();
            order.setReceiverDetailAddress(fullAddress);

            order.setNote(submitVo.getRemark());

            // 插入数据库
            orderTradeMapper.insert(order);
            log.info("商品[{}]生成订单成功, 订单ID: {}", item.getSkuId(), order.getId());
            orderIds.add(String.valueOf(order.getId()));
        }

        // 5. 清理购物车 (只清理选中的商品，没选的留着)
        for (CartItem item : selectedItems) {
            cartFeignClient.deleteItem(item.getSkuId());
        }

        return Result.success(String.join(",", orderIds));
    }
}