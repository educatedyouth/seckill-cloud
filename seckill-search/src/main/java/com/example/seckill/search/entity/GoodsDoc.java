package com.example.seckill.search.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品搜索索引实体 (SPU粒度)
 * 对应 ES 索引名: goods
 * * 架构师说明：
 * 1. indexName = "goods"：自动创建的索引名字。
 * 2. createIndex = true：项目启动时，如果索引不存在，会自动创建（生产环境通常关掉，开发环境开启方便调试）。
 * 3. @Field：定义映射类型，这决定了搜索的准确性。
 */
@Data
@Document(indexName = "goods", createIndex = true)
// 2. 【核心】引用我们在 resources 下写的配置文件
@Setting(settingPath = "/es-settings.json")
public class GoodsDoc {

    /**
     * 商品ID (SPU ID)
     * 对应 ES 的 _id
     */
    @Id
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 标题
     * 类型：Text (需要分词)
     * 分词器：ik_max_word (最大粒度分词，适合搜索)
     * 搜索分词器：ik_smart (智能分词，适合搜索词匹配)
     */
    // 3. 【核心】修改分词器为我们自定义的 my_smart_analyzer
    // searchAnalyzer 也指定一致，保证搜 "苹果" 和存 "苹果" 的逻辑一样
    @Field(type = FieldType.Text, analyzer = "my_smart_analyzer", searchAnalyzer = "my_smart_analyzer")
    private String title;
    /**
     * 副标题/卖点
     * 类型：Text (需要分词)
     */
    @Field(type = FieldType.Text, analyzer = "my_smart_analyzer", searchAnalyzer = "my_smart_analyzer")
    private String subTitle;

    /**
     * 展示价格 (取 SKU 最低价)
     * 类型：Double (用于价格区间筛选，Range Query)
     */
    @Field(type = FieldType.Double)
    private BigDecimal price;

    /**
     * 默认图片 (仅用于展示，不需要分词)
     * 类型：Keyword (不分词，节省空间)
     * index = false (通常不需要根据图片URL去搜索，设为false不建立倒排索引，只存数据)
     */
    @Field(type = FieldType.Keyword, index = false)
    private String img;

    /**
     * 品牌ID
     */
    @Field(type = FieldType.Long)
    private Long brandId;

    /**
     * 品牌名称
     * 类型：Keyword (用于精确匹配、聚合筛选，如侧边栏的【品牌选择】)
     */
    @Field(type = FieldType.Keyword)
    private String brandName;

    /**
     * 分类ID
     */
    @Field(type = FieldType.Long)
    private Long categoryId;

    /**
     * 分类名称
     * 类型：Keyword (用于精确匹配、聚合筛选)
     */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /**
     * 销量 (用于排序)
     */
    @Field(type = FieldType.Long)
    private Long saleCount;

    /**
     * 上架时间 (用于新品排序)
     */
    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Date createTime;
}