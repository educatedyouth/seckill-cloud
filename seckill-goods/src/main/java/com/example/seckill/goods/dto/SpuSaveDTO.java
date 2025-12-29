package com.example.seckill.goods.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品发布传输对象
 * 前端会将 SPU 基本信息、积分信息、SKU 列表一起传过来
 */
@Data
public class SpuSaveDTO {

    // --- SPU 基本信息 ---
    private String spuName;
    private String spuDescription;
    private Long categoryId;
    private Long brandId;
    private BigDecimal weight;
    private Integer publishStatus; // 0-下架 1-上架
    private String spuImg;
    // --- SPU 图片集 (逗号分隔的URL字符串，或者用 List<String>，这里为了方便数据库存取暂定 List<String>) ---
    private List<String> spuImages;

    // --- SKU 列表 (核心) ---
    private List<SkuSaveDTO> skus;
}