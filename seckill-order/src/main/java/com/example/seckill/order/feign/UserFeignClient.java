package com.example.seckill.order.feign;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.entity.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// value = "seckill-user": 指定要呼叫的服务名 (必须和 Nacos 里的名字一样)
@FeignClient(value = "seckill-user")
public interface UserFeignClient {

    // 这里定义的方法，要和 seckill-user 的 Controller 一模一样
    // 意思就是：当我调用这个方法时，Feign 会自动帮我发 HTTP 请求到 http://seckill-user/user/get/{id}
    @GetMapping("/user/get/{id}")
    Result<User> getUser(@PathVariable("id") Integer id);
}