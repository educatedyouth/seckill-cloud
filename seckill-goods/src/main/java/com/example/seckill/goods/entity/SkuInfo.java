package com.example.seckill.goods.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.io.Serializable;

@Data
@TableName("pms_sku_info")
public class SkuInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long skuId;
    private Long spuId;
    private String skuName;
    private String skuDesc;
    private Long categoryId;
    private Long brandId;
    private String skuDefaultImg;
    private String skuTitle;
    private String skuSubtitle;
    private BigDecimal price;
    private Long saleCount;

    // 我们刚才手动补加的字段，作为库存的“真理来源”
    private Integer stock;
}