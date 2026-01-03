package com.example.seckill.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.KnnQuery; // 【新增】KNN 查询对象
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    // 【核心修改】注入原生客户端，代替 ElasticsearchTemplate
    @Autowired
    private ElasticsearchClient elasticsearchClient;

    // AI 服务
    @Autowired
    private LlmService llmService;
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
        // ================== 【新增 AI 增强逻辑】 START ==================
        // 放在组装完 doc 之后，save 之前。使用 try-catch 保证 AI 异常不影响上架
        try {
            long start = System.currentTimeMillis();

            // A. 调用 DeepSeek 生成扩展关键词
            List<String> aiKeywords = llmService.expandKeywords(doc.getTitle(), doc.getSubTitle());
            doc.setAiKeywords(aiKeywords);

            // B. 调用 BGE-M3 生成向量
            // 拼接富文本：标题 + 简介 + 扩展关键词
            String richText = doc.getTitle() + " " + doc.getSubTitle() + " " + String.join(" ", aiKeywords);
            List<Float> vector = llmService.getVector(richText);

            if (vector != null && vector.size() == 1024) {
                doc.setEmbeddingVector(vector);
            }

            log.info(">>> [AI增强] SPU: {} | 耗时: {}ms | 关键词: {}", doc.getId(), (System.currentTimeMillis() - start), aiKeywords);
        } catch (Exception e) {
            log.error(">>> [AI增强] 失败，降级为普通索引，SPU: {}", doc.getId(), e);
            // 这里吞掉异常，确保即使 AI 挂了，商品也能正常上架被搜到（只是不智能）
        }
        // ================== 【新增 AI 增强逻辑】 END ==================
        // 3. 写入 ES
        goodsRepository.save(doc);
        log.info("商品上架同步成功：spuId={}, title={}", spuId, doc.getTitle());
        return true;
    }
    /**
     * 混合检索 (使用原生 Client 实现)
     */
    public SearchResultVO search(SearchParamDTO param) {
        SearchResultVO result = new SearchResultVO();

        // 1. 准备过滤条件 (Filter) - 抽离出来，供 Text 和 Vector 共用
        List<co.elastic.clients.elasticsearch._types.query_dsl.Query> filters = new ArrayList<>();

        if (param.getBrandId() != null) {
            filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.term(t -> t.field("brandId").value(param.getBrandId()))));
        }
        if (param.getCategoryId() != null) {
            filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.term(t -> t.field("categoryId").value(param.getCategoryId()))));
        }
        if (param.getPriceStart() != null || param.getPriceEnd() != null) {
            RangeQuery.Builder rangeBuilder = new RangeQuery.Builder().field("price");
            if (param.getPriceStart() != null) rangeBuilder.gte(JsonData.of(param.getPriceStart()));
            if (param.getPriceEnd() != null) rangeBuilder.lte(JsonData.of(param.getPriceEnd()));
            filters.add(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.range(rangeBuilder.build())));
        }

        // 2. 构建文本搜索 (Text Search)
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        // 2.1 必须匹配的文本
        if (StringUtils.hasText(param.getKeyword())) {
            boolQueryBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(param.getKeyword())
                    .fields("title^3", "subTitle^2", "brandName", "aiKeywords")
                    .fuzziness("AUTO")
                    .type(TextQueryType.BestFields)
                    .operator(Operator.Or)
            ));
        } else {
            boolQueryBuilder.must(m -> m.matchAll(ma -> ma));
        }
        // 2.2 应用过滤条件
        boolQueryBuilder.filter(filters);

        // 3. 构建请求
        // 建议先把 minScore 调低或者去掉，方便调试，等数据多了再加
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder().index("goods").minScore(0.4d);

        // 注入文本查询
        searchBuilder.query(q -> q.bool(boolQueryBuilder.build()));

        // 3. 【核心】向量检索 (KNN)
        if (StringUtils.hasText(param.getKeyword())) {
            try {
                // 1. 获取 Float 类型的向量 (LlmService 返回 List<Float>)
                List<Float> floatVector = llmService.getVector(param.getKeyword());

                if (floatVector != null && floatVector.size() == 1024) {
                    // 2. 【核心修正】将 List<Float> 转换为 List<Double>
                    // ES Java Client 的 queryVector 方法只接受 List<Double>
                    List<Double> doubleVector = floatVector.stream()
                            .map(Float::doubleValue)
                            .collect(Collectors.toList());

                    // 3. 构建 KNN 查询
                    searchBuilder.knn(k -> k
                            .field("embeddingVector")
                            .queryVector(doubleVector) // ✅ 这里传入 List<Double>
                            .k(10)
                            .numCandidates(100)
                            .boost(0.5f)
                    );
                    log.debug(">>> 已启用向量混合检索");
                }
            } catch (Exception e) {
                log.error(">>> 向量检索构建失败", e);
            }
        }

        // 5. 分页
        int from = (param.getPageNum() - 1) * param.getPageSize();
        searchBuilder.from(from).size(param.getPageSize());

        // 6. 排序 (Sort)
        // 注意：如果用户没有指定排序（默认综合排序），ES 会自动按 _score 降序，这是我们想要的（混合得分）
        if (StringUtils.hasText(param.getSort())) {
            String[] split = param.getSort().split("_");
            if (split.length == 2) {
                SortOrder order = "asc".equalsIgnoreCase(split[1]) ? SortOrder.Asc : SortOrder.Desc;
                String field = "price";
                if ("sale".equals(split[0])) field = "saleCount";
                if ("new".equals(split[0])) field = "createTime";
                String finalField = field;
                searchBuilder.sort(s -> s.field(f -> f.field(finalField).order(order)));
            }
        }

        // 7. 高亮
        if (StringUtils.hasText(param.getKeyword())) {
            searchBuilder.highlight(h -> h
                    .preTags("<span style='color:red'>")
                    .postTags("</span>")
                    .fields("title", f -> f)
                    .fields("subTitle", f -> f)
                    .fields("brandName", f -> f)
                    .fields("aiKeywords", f -> f)
            );
        }

        // 8. 执行与解析
        try {
            SearchResponse<GoodsDoc> response = elasticsearchClient.search(searchBuilder.build(), GoodsDoc.class);

            List<GoodsDoc> products = response.hits().hits().stream().map(hit -> {
                GoodsDoc doc = hit.source();
                if (doc != null) {
                    if (hit.highlight().containsKey("title")) {
                        doc.setTitle(hit.highlight().get("title").get(0));
                    }
                }
                return doc;
            }).collect(Collectors.toList());

            result.setProductList(products);
            long totalHits = response.hits().total().value();
            result.setTotal(totalHits);
            result.setPageNum(param.getPageNum());
            long totalPages = totalHits % param.getPageSize() == 0 ? totalHits / param.getPageSize() : totalHits / param.getPageSize() + 1;
            result.setTotalPages((int) totalPages);

        } catch (Exception e) {
            log.error("搜索执行失败", e);
            result.setProductList(new ArrayList<>());
            result.setTotal(0L);
            result.setTotalPages(0);
        }

        return result;
    }
}