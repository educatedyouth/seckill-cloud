package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("pms_sku_sale_attr_value")
public class SkuSaleAttrValue implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private Long skuId;
    private Long attrId;
    private String attrName;
    private String attrValue;
    private Integer attrSort;
}