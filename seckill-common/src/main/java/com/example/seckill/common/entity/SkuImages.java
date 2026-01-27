package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("pms_sku_images")
public class SkuImages implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long skuId;
    private String imgUrl;
    private Integer imgSort;
    private Integer defaultImg; // 0-否 1-是
}