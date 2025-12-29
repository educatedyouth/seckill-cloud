package com.example.seckill.goods.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SkuSaveDTO {
    // 【新增】SKU ID (用于后端判断是更新旧SKU还是插入新SKU)
    private Long skuId;
    private String skuName;
    private String skuTitle;
    private String skuSubtitle;
    private BigDecimal price;
    private Integer stock; // 库存

    private String defaultImg; // 默认展示图
    private List<String> images; // SKU 图集

    // 销售属性 (例如：[{"attrId":1,"attrName":"颜色","attrValue":"红色"}])
    private List<SkuSaleAttrDTO> saleAttrs;
}