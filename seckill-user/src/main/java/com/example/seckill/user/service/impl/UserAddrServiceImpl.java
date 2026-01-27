package com.example.seckill.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.common.entity.UserAddr;
import com.example.seckill.user.mapper.UserAddrMapper;
import com.example.seckill.user.service.UserAddrService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAddrServiceImpl extends ServiceImpl<UserAddrMapper, UserAddr> implements UserAddrService {

    @Override
    public List<UserAddr> listByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<UserAddr>()
                .eq(UserAddr::getUserId, userId)
                .orderByDesc(UserAddr::getIsDefault)
                .orderByDesc(UserAddr::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAddr(UserAddr userAddr, Long userId) {
        // 1. 归属权校验：查一下这个地址是不是这个人的
        UserAddr oldAddr = this.getOne(new LambdaQueryWrapper<UserAddr>()
                .eq(UserAddr::getId, userAddr.getId())
                .eq(UserAddr::getUserId, userId)); // 关键：带上 userId 查

        if (oldAddr == null) {
            throw new RuntimeException("地址不存在或无权修改"); // 生产环境建议用自定义异常 BusinessException
        }

        // 2. 补全 userId，防止前端恶意篡改
        userAddr.setUserId(userId);

        // 3. 执行更新
        return this.updateById(userAddr);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAddr(Long addrId, Long userId) {
        // 1. 归属权校验
        UserAddr oldAddr = this.getOne(new LambdaQueryWrapper<UserAddr>()
                .eq(UserAddr::getId, addrId)
                .eq(UserAddr::getUserId, userId));

        if (oldAddr == null) {
            throw new RuntimeException("地址不存在或无权删除");
        }

        // 2. 执行物理删除 (如果是逻辑删除，MP配置了会自动处理)
        return this.removeById(addrId);
    }
}