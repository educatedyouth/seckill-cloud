package com.example.seckill.common.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 秒杀异步下单消息体
 */
@Data
public class SeckillOrderMsgDTO implements Serializable {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long skuId;

    /**
     * 预生成的订单ID (基因ID)
     * 消费者必须使用这个ID入库，确保 Routing 一致性
     */
    private Long orderId;

    /**
     * 商品价格
     */
    private Long orderPrice;

    /**
     * 秒杀场次ID (可选，预留)
     */
    private Long activityId;
}