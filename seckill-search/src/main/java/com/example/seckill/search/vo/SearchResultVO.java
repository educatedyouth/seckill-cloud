package com.example.seckill.search.vo;

import com.example.seckill.search.entity.GoodsDoc;
import lombok.Data;
import java.util.List;

@Data
public class SearchResultVO {
    // 商品列表
    private List<GoodsDoc> productList;

    // 分页信息
    private Long total;
    private Integer totalPages;
    private Integer pageNum;

    // 聚合信息 (侧边栏筛选栏)
    // 比如搜"手机"，这里会返回 ["华为", "小米", "苹果"]
    private List<String> brandList;
    private List<String> categoryList;
}