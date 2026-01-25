package com.example.seckill.search.dto;

import lombok.Data;

@Data
public class SearchParamDTO {
    // 搜索关键字 (如 "手机")
    private String keyword;

    // 品牌ID过滤
    private Long brandId;

    // 分类ID过滤
    private Long categoryId;

    // 价格区间 (start - end)
    private Double priceStart;
    private Double priceEnd;

    // 排序规则: price_asc, price_desc, sale_desc, new_desc
    private String sort;

    // 分页参数
    private Integer pageNum = 1;
    private Integer pageSize = 20;
}