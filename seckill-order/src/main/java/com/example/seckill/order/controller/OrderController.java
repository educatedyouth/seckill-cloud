package com.example.seckill.order.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.order.service.OrderTradeService;
import com.example.seckill.order.vo.OrderSubmitVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderTradeService orderTradeService;

    @PostMapping("/create")
    public Result<String> createOrder(@RequestBody OrderSubmitVo submitVo) {
        return orderTradeService.createOrder(submitVo);
    }
}