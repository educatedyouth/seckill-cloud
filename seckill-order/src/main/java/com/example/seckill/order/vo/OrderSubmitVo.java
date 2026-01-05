package com.example.seckill.order.vo;

import lombok.Data;

@Data
public class OrderSubmitVo {
    private Long addrId; // 收货地址ID
    private Integer payType; // 支付方式 (1:支付宝 2:微信)
    private String orderToken; // 防重令牌
    private String remark; // 订单备注
}