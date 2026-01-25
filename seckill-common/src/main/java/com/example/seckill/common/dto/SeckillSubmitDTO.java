package com.example.seckill.common.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 秒杀下单请求参数
 */
@Data
public class SeckillSubmitDTO implements Serializable {
    /**
     * 商品ID
     */
    private Long skuId;

    /**
     * 商品价格
     */
    private Long orderPrice;

    /**
     * 收货地址ID
     */
    private Long addressId;

    /**
     * 秒杀令牌 (预留给后续的防刷验证)
     */
    private String seckillToken;
}