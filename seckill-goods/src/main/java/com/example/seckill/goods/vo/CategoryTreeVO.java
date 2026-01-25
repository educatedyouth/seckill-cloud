package com.example.seckill.goods.vo;

import lombok.Data;
import java.util.List;

@Data
public class CategoryTreeVO {
    private Long id;
    private String name;
    private Long parentId;
    private Integer catLevel;
    private Integer sort;
    private String icon;

    // 子分类列表
    private List<CategoryTreeVO> children;
}