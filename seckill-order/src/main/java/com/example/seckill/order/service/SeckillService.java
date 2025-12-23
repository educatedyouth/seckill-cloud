package com.example.seckill.order.service;

public interface SeckillService {
    /**
     * 秒杀核心接口
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 是否抢购提交成功 (注意：这里返回true只代表请求排队成功，不代表订单一定创建)
     */
    boolean seckill(int userId, int goodsId);
}