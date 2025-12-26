package com.example.seckill.goods.vo;

import com.example.seckill.goods.entity.SkuInfo;
import com.example.seckill.goods.entity.SpuInfo;
import lombok.Data;
import java.util.List;

/**
 * 商品详情页聚合视图对象
 */
@Data
public class GoodsDetailVO {
    // SPU 基本信息 (包含标题、描述)
    private SpuInfo spuInfo;

    // SKU 列表 (前端需要根据这个列表来计算：当选中"红色"时，"128G"是否还有货)
    private List<SkuInfo> skuList;

    // 这里通常还需要 "销售属性组合" (SaleAttrGroup)，
    // 但为了降低复杂度，目前第一版我们可以先让前端直接处理 skuList
    // 后续在 "SKU 联动" 环节如果性能不够，再补全后端预计算逻辑
}