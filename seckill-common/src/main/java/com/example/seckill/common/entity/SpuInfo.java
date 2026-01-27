package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.io.Serializable;

@Data
@TableName("pms_spu_info")
public class SpuInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String spuName;
    private String spuDescription;
    private Long categoryId;
    private Long brandId;
    private BigDecimal weight;
    private Integer publishStatus; // 0下架 1上架
    private Date createTime;
    private Date updateTime;
    // === 新增这一行 ===
    private String spuImg; // 对应数据库的 spu_img 字段
    // 【新增】记录商品归属人 (关键修复)
    private Long userId;
}