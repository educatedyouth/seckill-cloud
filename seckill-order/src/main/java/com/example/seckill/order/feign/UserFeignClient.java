package com.example.seckill.order.feign;

import com.example.seckill.common.entity.UserAddr;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "seckill-user")
public interface UserFeignClient {

    @GetMapping("/user/get/{id}")
    Result<User> getUser(@PathVariable("id") Integer id);

    /**
     * 远程调用查询地址详情
     * 路径必须与 UserAddrController 中的定义完全匹配
     */
    @GetMapping("/user/addr/info/{id}")
    Result<UserAddr> getUserAddrInfo(@PathVariable("id") Long id);
}