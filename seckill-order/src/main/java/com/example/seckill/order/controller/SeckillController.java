package com.example.seckill.order.controller;

import com.example.seckill.common.dto.SeckillSubmitDTO;
import com.example.seckill.common.result.Result;
import com.example.seckill.order.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀核心接口 (生产者)
 */
@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 执行秒杀 (异步排队)
     * POST /seckill/do_seckill
     * * @param submitDTO 参数 (skuId, addressId, token)
     * @return 订单ID (用于轮询) 或 错误信息
     */
    @PostMapping("/do_seckill")
    public Result<String> doSeckill(@RequestBody SeckillSubmitDTO submitDTO) {
        // 参数校验
        if (submitDTO.getSkuId() == null) {
            return Result.error("商品信息不能为空");
        }

        // 调用 Service
        return seckillService.processSeckillRequest(submitDTO);
    }
}