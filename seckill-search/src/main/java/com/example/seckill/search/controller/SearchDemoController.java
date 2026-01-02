package com.example.seckill.search.controller;

import com.example.seckill.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search/demo")
public class SearchDemoController {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private final String DEMO_INDEX = "goods_demo";

    /**
     * 1. 初始化 Demo 环境
     * 这一步会：删除旧索引 -> 创建带自定义配置的新索引 -> 写入测试数据
     */
    @GetMapping("/init")
    public Result<String> init() {
        IndexCoordinates indexCoordinates = IndexCoordinates.of(DEMO_INDEX);

        // 1.1 如果存在先删除 (保证环境纯净)
        if (elasticsearchOperations.indexOps(indexCoordinates).exists()) {
            elasticsearchOperations.indexOps(indexCoordinates).delete();
        }

        // 1.2 定义 Settings (核心：同义词 + 分词器)
        // 这里我们定义了一个名为 "my_smart_analyzer" 的自定义分词器
        Map<String, Object> settings = new HashMap<>();
        Map<String, Object> analysis = new HashMap<>();
        Map<String, Object> filter = new HashMap<>();
        Map<String, Object> analyzer = new HashMap<>();

        // 定义同义词过滤器
        Map<String, Object> synonymFilter = new HashMap<>();
        synonymFilter.put("type", "synonym_graph"); // 使用 graph 模式处理多词同义
        synonymFilter.put("synonyms", new String[]{
                "iphone, 苹果, apple",          // 搜苹果 -> 命中 iPhone
                "mate60, 遥遥领先",             // 搜遥遥领先 -> 命中 Mate60
                "iphone17, iphone 17"          // 解决粘连词问题：把 iphone17 视为 iphone 17
        });
        filter.put("my_synonym_filter", synonymFilter);

        // 定义自定义分词器
        Map<String, Object> myAnalyzer = new HashMap<>();
        // 【核心修复】必须显式指定类型为 "custom"
        myAnalyzer.put("type", "custom");
        myAnalyzer.put("tokenizer", "ik_max_word");
        myAnalyzer.put("filter", new String[]{ "lowercase", "my_synonym_filter" });
        analyzer.put("my_smart_analyzer", myAnalyzer);

        analysis.put("filter", filter);
        analysis.put("analyzer", analyzer);
        settings.put("analysis", analysis);

        // 1.3 定义 Mapping (告诉 ES 哪个字段用这个分词器)
        Document mapping = Document.create();
        mapping.put("properties", new HashMap<String, Object>() {{
            put("title", new HashMap<String, Object>() {{
                put("type", "text");
                put("analyzer", "my_smart_analyzer");       // 写入时用它
                put("search_analyzer", "my_smart_analyzer"); // 搜索时也用它
            }});
        }});

        // 1.4 执行创建索引
        elasticsearchOperations.indexOps(indexCoordinates).create(settings, mapping);

        // 1.5 写入测试数据
        saveDoc("1", "iPhone 17 Pro Max 钛金属");
        saveDoc("2", "华为 Mate60 Pro 雅川青");
        saveDoc("3", "普通的红富士苹果"); // 干扰项，测试会不会混淆

        return Result.success("Demo 索引初始化完成，同义词库已加载！");
    }

    private void saveDoc(String id, String title) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", id);
        doc.put("title", title);
        elasticsearchOperations.save(doc, IndexCoordinates.of(DEMO_INDEX));
    }

    /**
     * 2. 执行搜索测试
     */
    @GetMapping("/query")
    public Result<List<Map<String, Object>>> query(@RequestParam String keyword) {
        // 构建原生查询 DSL
        String queryDsl = String.format("""
            {
                "match": {
                    "title": {
                        "query": "%s",
                        "analyzer": "my_smart_analyzer"
                    }
                }
            }
            """, keyword);

        // 执行查询
        org.springframework.data.elasticsearch.core.query.StringQuery query =
                new org.springframework.data.elasticsearch.core.query.StringQuery(queryDsl);

        var hits = elasticsearchOperations.search(query, Map.class, IndexCoordinates.of(DEMO_INDEX));

        // 提取结果
        List<Map<String, Object>> resultList = new ArrayList<>();
        hits.forEach(hit -> {
            resultList.add(hit.getContent());
        });

        return Result.success(resultList);
    }
}