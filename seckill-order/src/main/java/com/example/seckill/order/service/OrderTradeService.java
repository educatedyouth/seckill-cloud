package com.example.seckill.order.service;

import com.example.seckill.common.result.Result;
import com.example.seckill.order.vo.OrderSubmitVo;

public interface OrderTradeService {
    /**
     * 创建普通交易订单
     */
    Result<String> createOrder(OrderSubmitVo submitVo);
}