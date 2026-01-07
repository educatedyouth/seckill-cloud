package com.example.seckill.order.service;

import com.example.seckill.common.dto.SeckillSubmitDTO;
import com.example.seckill.common.result.Result;

public interface SeckillService {

    /**
     * 处理秒杀请求 (核心高并发接口)
     * 1. 本地缓存校验
     * 2. Redis Lua 预扣减
     * 3. 发送 MQ 异步下单
     * * @param submitDTO 秒杀提交参数
     * @return 结果
     */
    Result<String> processSeckillRequest(SeckillSubmitDTO submitDTO);
}