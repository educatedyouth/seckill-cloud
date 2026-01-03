package com.example.seckill.search.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.search.service.LlmService;
import com.example.seckill.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search/test")
public class SearchTestController {

    @Autowired
    private SearchService searchService;

    /**
     * 手动触发上架同步
     * URL: http://localhost:8050/search/test/up/1
     */
    @GetMapping("/up/{spuId}")
    public Result<String> testUp(@PathVariable Long spuId) {
        boolean success = searchService.syncUp(spuId);
        return success ? Result.success("同步成功") : Result.error("同步失败");
    }

    @Autowired
    private LlmService llmService;

    @GetMapping("/ai/infra")
    public Result<Map<String, Object>> testAiInfra(@RequestParam String text) {
        Map<String, Object> result = new HashMap<>();

        // 1. 测试扩充
        List<String> keywords = llmService.expandKeywords(text, "测试商品描述");
        result.put("keywords", keywords);

        // 2. 测试向量
        List<Float> vector = llmService.getVector(text);
        result.put("vector_size", vector.size()); // 应该是 1024
        result.put("vector_sample", vector.subList(0, Math.min(5, vector.size()))); // 看前5位
        result.put("vector_type", vector.get(0).getClass().getSimpleName()); // 确认是 Float

        return Result.success(result);
    }
}