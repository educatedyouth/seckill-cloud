package com.example.seckill.user.controller;

import com.example.seckill.common.context.UserContext;
import com.example.seckill.common.entity.UserAddr;
import com.example.seckill.common.result.Result;
import com.example.seckill.user.service.UserAddrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/addr")
public class UserAddrController {

    @Autowired
    private UserAddrService userAddrService;

    /**
     * 列表查询
     */
    @GetMapping("/list")
    public Result<List<UserAddr>> list() {
        Long userId = UserContext.getUserId();
        return Result.success(userAddrService.listByUserId(userId));
    }

    /**
     * 新增地址
     */
    @PostMapping("/add")
    public Result<String> add(@RequestBody UserAddr userAddr) {
        Long userId = UserContext.getUserId();
        userAddr.setUserId(userId);
        userAddr.setId(null); // 强制置空ID，防止覆盖
        userAddrService.save(userAddr);
        return Result.success("添加成功");
    }

    /**
     * 【补全】修改地址
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody UserAddr userAddr) {
        // 参数校验
        if (userAddr.getId() == null) {
            return Result.error("地址ID不能为空");
        }

        Long userId = UserContext.getUserId();
        try {
            // 调用包含校验逻辑的 Service
            userAddrService.updateAddr(userAddr, userId);
            return Result.success("修改成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【补全】删除地址
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        try {
            userAddrService.deleteAddr(id, userId);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【新增】根据地址ID获取详细信息 (供订单服务远程调用)
     */
    @GetMapping("/info/{id}")
    public Result<UserAddr> getUserAddrInfo(@PathVariable("id") Long id) {
        UserAddr userAddr = userAddrService.getById(id);
        if (userAddr == null) {
            return Result.error("地址不存在");
        }
        return Result.success(userAddr);
    }
}