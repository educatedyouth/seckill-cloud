package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("user_addr")
public class UserAddr implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 收货人信息
    private String receiverName;
    private String receiverPhone;

    // 区域信息
    private String province;
    private String city;
    private String area;
    private String detailAddr;

    // 是否默认：0-否，1-是
    private Integer isDefault;

    private Date createTime;
    private Date updateTime;
}