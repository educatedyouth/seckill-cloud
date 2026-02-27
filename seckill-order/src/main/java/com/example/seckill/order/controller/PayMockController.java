package com.example.seckill.order.controller;

import com.example.seckill.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 支付服务模拟接口 (TODO: 对接真实支付网关)
 */
@Slf4j
@RestController
@RequestMapping("/pay/mock")
public class PayMockController {

    private final Random random = new Random();

    /**
     * 模拟查询订单支付状态
     * 供延时关单消费者调用，用于判断是否需要真正关单
     *
     * @param orderId 订单ID
     * @return true=已支付(不关单), false=未支付(需要关单)
     */
    @GetMapping("/check/{orderId}")
    public Result<Boolean> checkPayStatus(@PathVariable("orderId") Long orderId) {
        log.info(">>> [模拟支付网关] 正在查询订单 {} 的支付状态...", orderId);

        try {
            // 1. 模拟网络延时 (50ms - 500ms)
            int sleepTime = 50 + random.nextInt(450);
            TimeUnit.MILLISECONDS.sleep(sleepTime);

            // 2. 模拟支付结果 (80% 概率未支付，方便你测试自动关单逻辑)
            // random.nextInt(10) 生成 0-9。 如果大于 7 (即 8,9) 则算支付成功
            boolean isPaid = random.nextInt(10) > 7;

            log.info(">>> [模拟支付网关] 订单 {} 查询完成, 耗时: {}ms, 状态: {}",
                    orderId, sleepTime, isPaid ? "已支付" : "未支付");

            return Result.success(isPaid);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("500");
        }
    }

    /**
     * 手动强制标记为已支付 (方便手动测试)
     */
    @PostMapping("/notify/{orderId}")
    public Result<String> mockPaySuccess(@PathVariable("orderId") Long orderId) {
        // 在真实业务中，这里会更新数据库 order_tbl_x 的 status 为 2 (已支付)
        log.info(">>> [模拟支付回调] 收到订单 {} 的支付成功通知", orderId);
        // 这里只是打印日志，你需要在这里补充调用 orderMapper 更新状态的代码
        // 或者仅仅作为一个触发器，你的逻辑里真正更新状态应该是在 Service 层
        return Result.success("模拟回调成功");
    }
}