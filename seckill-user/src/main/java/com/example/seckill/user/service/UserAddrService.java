package com.example.seckill.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckill.common.entity.UserAddr;
import java.util.List;

public interface UserAddrService extends IService<UserAddr> {

    /**
     * 查询某用户的所有地址
     */
    List<UserAddr> listByUserId(Long userId);

    /**
     * 【新】更新地址（包含归属权校验）
     * @param userAddr 待更新的地址信息（必须包含ID）
     * @param userId 当前操作的用户ID
     * @return 是否成功
     */
    boolean updateAddr(UserAddr userAddr, Long userId);

    /**
     * 【新】删除地址（包含归属权校验）
     * @param addrId 地址ID
     * @param userId 当前操作的用户ID
     * @return 是否成功
     */
    boolean deleteAddr(Long addrId, Long userId);
}