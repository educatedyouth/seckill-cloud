package com.example.seckill.auth.feign;

import com.example.seckill.common.entity.User;
import com.example.seckill.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "seckill-user")
public interface UserFeignClient {

    // 调用刚才写的内部接口
    @GetMapping("/user/internal/get/{username}")
    Result<User> getUserByUsername(@PathVariable("username") String username);
}