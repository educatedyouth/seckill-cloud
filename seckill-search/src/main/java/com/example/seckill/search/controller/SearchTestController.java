package com.example.seckill.search.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.search.dto.SearchParamDTO;
import com.example.seckill.search.service.LlmService;
import com.example.seckill.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    // 线程池
    private final ExecutorService executor = Executors.newFixedThreadPool(200);

    /**
     * 🚀 远程遥控压测接口 (本地缓存版)
     * URL: http://localhost:8050/search/test/benchmark?mode=gpu&threads=-1&count=15000
     */
    @GetMapping("/benchmark")
    public Result<Map<String, Object>> benchmark(
            @RequestParam(defaultValue = "gpu") String mode,
            @RequestParam(defaultValue = "50") int threads,
            @RequestParam(defaultValue = "2000") int count,
            @RequestParam(defaultValue = "手机") String keyword
    ) {
        SearchParamDTO mockParam = new SearchParamDTO();
        mockParam.setKeyword(keyword);
        mockParam.setPageNum(1);
        mockParam.setPageSize(20);

        List<Callable<Long>> tasks = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            tasks.add(() -> {
                long start = System.nanoTime();
                try {
                    // 这里调用的 searchByGPU 已经是走本地缓存的版本了，极快
                    if ("gpu".equalsIgnoreCase(mode)) {
                        searchService.searchByGPU(mockParam);
                    } else {
                        searchService.search(mockParam);
                    }
                } catch (Exception e) {
                }
                return (System.nanoTime() - start) / 1_000_000; // ms
            });
        }

        long wallClockStart = System.currentTimeMillis();
        List<Long> latencies = new ArrayList<>();
        try {
            List<Future<Long>> futures = executor.invokeAll(tasks);
            for (Future<Long> f : futures) {
                latencies.add(f.get());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("压测中断: " + e.getMessage());
        }
        long wallClockEnd = System.currentTimeMillis();

        if (latencies.isEmpty()) return Result.error("无数据");
        Collections.sort(latencies);

        long duration = wallClockEnd - wallClockStart;
        double qps = (double) count / (duration / 1000.0);
        double avg = latencies.stream().mapToLong(v -> v).average().orElse(0);
        long p99 = latencies.get((int)(latencies.size() * 0.99) - 1);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("模式", mode.toUpperCase());
        report.put("QPS", String.format("%.2f", qps));
        report.put("P99(ms)", p99);
        report.put("Avg(ms)", String.format("%.2f", avg));
        report.put("TotalTime(ms)", duration);

        return Result.success(report);
    }
}