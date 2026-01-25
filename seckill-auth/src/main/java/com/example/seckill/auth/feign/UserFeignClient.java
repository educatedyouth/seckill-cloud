package com.example.seckill.auth.feign;

import com.example.seckill.common.entity.User;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.vo.RegisterVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "seckill-user")
public interface UserFeignClient {

    // 调用刚才写的内部接口
    @GetMapping("/user/internal/get/{username}")
    Result<User> getUserByUsername(@PathVariable("username") String username);

    // 【新增】注册接口，调用 seckill-user 的保存服务
    @PostMapping("/user/register")
    Result<User> register(@RequestBody RegisterVo registerVo);
}