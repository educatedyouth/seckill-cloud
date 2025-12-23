package com.example.seckill.order.controller;

import com.example.seckill.order.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 秒杀请求入口
     * 测试链接: http://localhost:8080/seckill/do_seckill?userId=1001&goodsId=1001
     * * @param userId 模拟用户ID
     * @param goodsId 模拟商品ID
     * @return 简单的 String 提示
     */
    @GetMapping("/do_seckill")
    public String doSeckill(@RequestParam("userId") int userId,
                            @RequestParam("goodsId") int goodsId) {

        System.out.printf(">>> 收到秒杀请求: User=%d, Goods=%d%n", userId, goodsId);

        // 调用 Service 发送事务消息
        boolean isSuccess = seckillService.seckill(userId, goodsId);

        if (isSuccess) {
            return "排队成功！请求已发送给 MQ，请稍后查询订单状态。";
        } else {
            return "抢购失败！库存不足 或 系统繁忙。";
        }
    }
}