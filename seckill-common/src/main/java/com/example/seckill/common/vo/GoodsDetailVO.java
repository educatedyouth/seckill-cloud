package com.example.seckill.common.vo;

import com.example.seckill.common.entity.SkuImages;
import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.entity.SkuSaleAttrValue;
import com.example.seckill.common.entity.SpuInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 商品详情页聚合视图对象 (Product Detail Page VO)
 * 架构师批注：这是前后端交互的核心协议，包含了渲染详情页所需的所有数据
 */
@Data
public class GoodsDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 1. SPU 基本信息 (标题、描述、SPU图)
    private SpuInfo spuInfo;

    // 2. 增强版 SKU 列表 (前端不仅需要价格库存，还需要知道每个SKU对应的规格属性)
    private List<SkuItem> skuList;

    /**
     * 内部静态类：SKU 聚合条目
     * 继承 SkuInfo 获取基础字段，同时扩展 属性列表 和 图片列表
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SkuItem extends SkuInfo {
        // 该 SKU 对应的销售属性组合 (e.g. [{"颜色","红"}, {"内存","128G"}])
        private List<SkuSaleAttrValue> saleAttrValues;

        // 该 SKU 的特定图片集 (如果有的话)
        private List<SkuImages> images;
    }
}