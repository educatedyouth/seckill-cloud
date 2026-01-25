package com.example.seckill.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

import java.util.*;
import java.math.BigDecimal;
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
     * 阶段一：基础同步 (Fast)
     * 仅负责将 MySQL 数据搬运到 ES，不进行任何 AI 调用
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

        // 2. 数据转换 (只做基础字段)
        GoodsDoc doc = new GoodsDoc();
        doc.setId(spuInfo.getId());
        doc.setTitle(spuInfo.getSpuName());
        doc.setSubTitle(spuInfo.getSpuDescription());
        doc.setBrandId(spuInfo.getBrandId());
        doc.setCategoryId(spuInfo.getCategoryId());
        doc.setCreateTime(spuInfo.getCreateTime());
        doc.setBrandName(""); // 暂空，后续聚合优化
        doc.setCategoryName(""); // 暂空

        // 计算价格与销量
        if (skuList != null && !skuList.isEmpty()) {
            BigDecimal minPrice = skuList.stream()
                    .map(SkuInfo::getPrice)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            doc.setPrice(minPrice);

            String defaultImg = skuList.get(0).getSkuDefaultImg();
            doc.setImg(defaultImg != null ? defaultImg : "");

            long totalSale = skuList.stream()
                    .mapToLong(sku -> sku.getSaleCount() == null ? 0L : sku.getSaleCount())
                    .sum();
            doc.setSaleCount(totalSale);
        }

        // 3. 写入 ES (此时没有 AI 关键词和向量，但已经可以被 ID 或 精确 Title 搜到了)
        goodsRepository.save(doc);
        log.info(">>> [阶段1] 基础数据同步完成：spuId={}", spuId);
        return true;
    }

    /**
     * 阶段二：AI 增强 (Slow)
     * 异步调用，执行耗时的 LLM 和 Embedding
     */
    public void syncAiStrategy(Long spuId) {
        // 1. 先从 ES 查询现有的文档 (避免再次调用 Feign，或者为了数据新鲜度也可以调 Feign，这里查 ES 即可)
        // 注意：Optional 处理
        GoodsDoc doc = goodsRepository.findById(spuId).orElse(null);
        if (doc == null) {
            log.warn(">>> [AI增强] 未找到商品文档，可能已被删除，停止增强 spuId={}", spuId);
            return;
        }

        try {
            long start = System.currentTimeMillis();

            // 2. 生成关键词
            List<String> aiKeywords = llmService.expandKeywords(doc.getTitle(), doc.getSubTitle());
            doc.setAiKeywords(aiKeywords);

            // 3. 生成向量
            // 拼接富文本：标题 + 简介 + 扩展关键词
            String richText = doc.getTitle() + " " + doc.getSubTitle() + " " + String.join(" ", aiKeywords);
            List<Float> vector = llmService.getVector(richText);

            if (vector != null && vector.size() == 1024) {
                doc.setEmbeddingVector(vector);
            }

            // 4. 更新 ES (这里是 Update 操作)
            goodsRepository.save(doc);
            log.info(">>> [阶段2] AI 增强完成 ({}ms) | spuId={}", (System.currentTimeMillis() - start), spuId);

        } catch (Exception e) {
            log.error(">>> [AI增强] 失败 spuId={}", spuId, e);
            // AI 失败不回滚基础数据，保证商品依然在架
        }
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
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder().index("goods").minScore(0.6d);

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