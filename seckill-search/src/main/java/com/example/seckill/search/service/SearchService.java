package com.example.seckill.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;
import com.alibaba.nacos.common.utils.StringUtils;
import com.example.seckill.common.result.Result;
import com.example.seckill.common.vo.GoodsDetailVO;
import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.entity.SpuInfo;
import com.example.seckill.search.dto.SearchParamDTO;
import com.example.seckill.search.entity.GoodsDoc;
import com.example.seckill.search.feign.GoodsFeignClient;
import com.example.seckill.search.repository.GoodsRepository;
import com.example.seckill.search.vo.SearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;

import java.util.Collections;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchService {

    @Autowired
    private GoodsFeignClient goodsFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private ElasticsearchTemplate esTemplate;
    /**
     * 上架商品同步 (MySQL -> ES)
     * @param spuId 商品ID
     * @return 是否成功
     */
    public boolean syncUp(Long spuId) {
        // 1. 远程调用获取商品详情
        Result<GoodsDetailVO> result = goodsFeignClient.getGoodsDetail(spuId);
        if (result == null || result.getData() == null) {
            log.error("同步失败：无法获取商品信息 spuId={}", spuId);
            return false;
        }

        GoodsDetailVO vo = result.getData();
        SpuInfo spuInfo = vo.getSpuInfo();
        List<GoodsDetailVO.SkuItem> skuList = vo.getSkuList();

        // 2. 数据转换 (Data Mapping)
        GoodsDoc doc = new GoodsDoc();
        // 2.1 基础信息拷贝
        doc.setId(spuInfo.getId());
        doc.setTitle(spuInfo.getSpuName()); // SPU名作为搜索标题
        doc.setSubTitle(spuInfo.getSpuDescription());
        doc.setBrandId(spuInfo.getBrandId());
        doc.setCategoryId(spuInfo.getCategoryId());
        doc.setCreateTime(spuInfo.getCreateTime());

        // 为了后续聚合方便，这里暂时存个空字符串，或者你可以在 GoodsDetailVO 里补充 BrandName/CategoryName
        // 这里的逻辑：如果有通过 Feign 拿到名字最好，如果没有，暂时置空，不影响核心搜索
        doc.setBrandName("");
        doc.setCategoryName("");

        // 2.2 计算价格 (取所有 SKU 中的最低价)
        // ✅ 修改为以下防御性代码 (Line 60-65 左右)
        if (skuList != null && !skuList.isEmpty()) {
            BigDecimal minPrice = skuList.stream()
                    .map(SkuInfo::getPrice)
                    .filter(price -> price != null) // 【核心修复】过滤掉 null 价格
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            doc.setPrice(minPrice);

            // 设置图片等逻辑保持不变
            // 同时也建议加个判空，防止图片也是 null 导致后续报错
            String defaultImg = skuList.get(0).getSkuDefaultImg();
            doc.setImg(defaultImg != null ? defaultImg : "");

            long totalSale = skuList.stream()
                    .mapToLong(sku -> sku.getSaleCount() == null ? 0L : sku.getSaleCount()) // 防御性处理销量 null
                    .sum();
            doc.setSaleCount(totalSale);
        }

        // 3. 写入 ES
        goodsRepository.save(doc);
        log.info("商品上架同步成功：spuId={}, title={}", spuId, doc.getTitle());
        return true;
    }
    /**
     * 核心搜索接口
     */
    public SearchResultVO search(SearchParamDTO param) {
        SearchResultVO result = new SearchResultVO();

        // 1. 构建动态查询条件 (BoolQuery)
        // Spring Data ES 5.x/Spring Boot 3 使用 co.elastic.clients 的构建器
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        // 1. 关键字搜索 (核心升级：从 Match 升级为 MultiMatch)
        if (StringUtils.hasText(param.getKeyword())) {
            boolQueryBuilder.must(m -> m
                    .multiMatch(mm -> mm
                            .query(param.getKeyword()) // 用户输入的词
                            // 1.1 指定搜索字段及权重 (Boosting)
                            // title^3 表示标题命中的分数是其他的3倍
                            .fields("title^3", "subTitle^2", "brandName", "categoryName")

                            // 1.2 开启模糊匹配 (Fuzziness)
                            // "AUTO" 允许根据词长度自动容错 (比如 iphoe -> iphone)
                            .fuzziness("AUTO")

                            // 1.3 匹配策略
                            // BEST_FIELDS: 只要有一个字段匹配就算分
                            // MOST_FIELDS: 多个字段匹配累加分 (适合全文检索)
                            .type(TextQueryType.BestFields)

                            // 1.4 逻辑操作
                            // OR: 只要分词后的任意一个词匹配即可 (召回率高，iPhone 17 -> 搜 iPhone 或 17)
                            // AND: 必须所有分词都匹配 (精准度高)
                            // 建议用 OR 配合 minimumShouldMatch 提高体验，这里简单演示用 OR
                            .operator(Operator.Or)
                    )
            );
        } else {
            boolQueryBuilder.must(m -> m.matchAll(ma -> ma));
        }

        // 1.2 过滤条件 (Filter - 不计算相关性得分，性能高)
        // 品牌
        if (param.getBrandId() != null) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("brandId").value(param.getBrandId())));
        }
        // 分类
        if (param.getCategoryId() != null) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("categoryId").value(param.getCategoryId())));
        }
        // 价格区间
        if (param.getPriceStart() != null || param.getPriceEnd() != null) {
            RangeQuery.Builder rangeBuilder = new RangeQuery.Builder().field("price");
            if (param.getPriceStart() != null) rangeBuilder.gte(JsonData.of(param.getPriceStart()));
            if (param.getPriceEnd() != null) rangeBuilder.lte(JsonData.of(param.getPriceEnd()));
            boolQueryBuilder.filter(f -> f.range(rangeBuilder.build()));
        }

        // 2. 构建原生查询对象 (NativeQuery)
        NativeQueryBuilder nativeQueryBuilder = NativeQuery.builder()
                .withQuery(q -> q.bool(boolQueryBuilder.build()));

        // 2.1 排序
        if (StringUtils.hasText(param.getSort())) {
            // 格式: price_asc
            String[] split = param.getSort().split("_");
            if (split.length == 2) {
                Sort.Direction direction = "asc".equalsIgnoreCase(split[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
                // sale_desc -> saleCount, price_asc -> price
                String field = "price";
                if ("sale".equals(split[0])) field = "saleCount";
                if ("new".equals(split[0])) field = "createTime";

                nativeQueryBuilder.withSort(Sort.by(direction, field));
            }
        }

        // 2.2 分页
        nativeQueryBuilder.withPageable(PageRequest.of(param.getPageNum() - 1, param.getPageSize()));

        // 2.3 高亮 (Highlight) - 修正版
        if (StringUtils.hasText(param.getKeyword())) {
            // 1. 定义高亮参数：设置红色的 span 标签
            HighlightParameters parameters = HighlightParameters.builder()
                    .withPreTags("<span style='color:red'>")
                    .withPostTags("</span>")
                    .build();

            // 2. 定义高亮字段：指定对 'title' 字段进行高亮
            HighlightField highlightField = new HighlightField("title");

            // 3. 构建 Highlight 对象
            Highlight highlight = new Highlight(parameters, Collections.singletonList(highlightField));

            // 4. 注入到 NativeQuery 中
            nativeQueryBuilder.withHighlightQuery(new HighlightQuery(highlight, GoodsDoc.class));
        }

        // 3. 执行搜索
        SearchHits<GoodsDoc> hits = esTemplate.search(nativeQueryBuilder.build(), GoodsDoc.class);

        // 4. 解析结果
        List<GoodsDoc> products = hits.getSearchHits().stream().map(hit -> {
            GoodsDoc doc = hit.getContent();
            // 处理高亮替换
            List<String> highlightTitle = hit.getHighlightField("title");
            if (highlightTitle != null && !highlightTitle.isEmpty()) {
                doc.setTitle(highlightTitle.get(0));
            }
            return doc;
        }).collect(Collectors.toList());

        result.setProductList(products);
        result.setTotal(hits.getTotalHits());
        result.setPageNum(param.getPageNum());
        // 计算总页数
        long totalPages = hits.getTotalHits() % param.getPageSize() == 0 ?
                hits.getTotalHits() / param.getPageSize() :
                hits.getTotalHits() / param.getPageSize() + 1;
        result.setTotalPages((int) totalPages);

        return result;
    }
}