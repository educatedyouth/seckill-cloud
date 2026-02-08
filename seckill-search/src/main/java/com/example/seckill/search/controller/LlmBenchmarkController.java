package com.example.seckill.search.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.search.service.LlmBatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/llm/test")
public class LlmBenchmarkController {

    @Autowired
    private LlmBatchService llmBatchService;

    // 独立的线程池，避免干扰业务
    private final ExecutorService executor = Executors.newFixedThreadPool(200);

    // 预定义的 Prompt 模板 (模拟真实的业务场景)
    private static final String SYSTEM_PROMPT =
            "你是一个电商搜索优化专家。请根据商品标题和简介，扩展生成一行5-10个中文搜索关键词，不要分段，不要编号，关键词之间用逗号分隔，要求包含同义词，功能词，场景词等简短词汇。\n";

    /**
     * LLM 本地推理压测接口 (Update: 适配真实业务调用链路)
     * URL: http://localhost:8050/llm/test/benchmark?threads=32&count=100
     */
    @GetMapping("/benchmark")
    public Result<Map<String, Object>> benchmark(
            @RequestParam(defaultValue = "-1") int threads,
            @RequestParam(defaultValue = "100") int count,
            // 默认传入一个真实的商品信息片段
            @RequestParam(defaultValue = "商品标题：苹果耳机\n商品简介：airpods pro 2代，非凡音质") String userPrompt
    ) {
        // 1. 准备任务
        List<Callable<Long>> tasks = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            tasks.add(() -> {
                long start = System.nanoTime();
                try {
                    // --- 步骤 1: 拼接业务 Prompt ---
                    String formattedPrompt = SYSTEM_PROMPT + userPrompt;

                    // --- 步骤 2: 发起异步调用 ---
                    CompletableFuture<LlmBatchService.futureRes> future = llmBatchService.asyncChatWordVec(formattedPrompt);

                    // --- 步骤 3: 阻塞获取结果 (设置 300s 业务兜底超时) ---
                    // 这里模拟真实的业务等待，如果超时抛出 TimeoutException，压测记录为失败
                    LlmBatchService.futureRes res = future.get(300, TimeUnit.SECONDS);

                    // --- 步骤 4: 校验数据完整性 ---
                    if (res == null || res.futureWord == null || res.futureVec == null) {
                        return -1L; // 业务层面的空结果，视为失败
                    }

                    // 模拟消费数据
                    List<String> keywords = res.futureWord;
                    float[] vector = res.futureVec;

                    if (keywords.isEmpty() || vector.length != 1024) { // 假设 BGE-M3 维度是 1024
                        // 可以在这里打印异常日志，但压测时为了性能通常忽略
                        return -1L;
                    }

                } catch (Exception e) {
                    // 包含 TimeoutException, ExecutionException, InterruptedException
                    return -1L;
                }
                return (System.nanoTime() - start) / 1_000_000; // 转为毫秒
            });
        }

        // 2. 执行压测
        long wallStart = System.currentTimeMillis();
        List<Long> latencies = new ArrayList<>();
        try {
            List<Future<Long>> futures = executor.invokeAll(tasks);
            for (Future<Long> f : futures) {
                long latency = f.get();
                if (latency > 0) { // 只统计成功的请求
                    latencies.add(latency);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("压测执行异常: " + e.getMessage());
        }
        long wallEnd = System.currentTimeMillis();

        // 3. 计算指标
        if (latencies.isEmpty()) return Result.error("所有请求均失败 (可能是超时或模型加载失败)");

        Collections.sort(latencies);

        long p99 = latencies.get((int) (latencies.size() * 0.99) - 1);
        long p95 = latencies.get((int) (latencies.size() * 0.95) - 1);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = latencies.get(latencies.size() - 1);
        long min = latencies.get(0);

        long duration = wallEnd - wallStart;
        // QPS = 成功请求数 / 总耗时
        double qps = (double) latencies.size() / (duration / 1000.0);

        // 4. 返回报告
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("Test Scenario", "LLM Native Inference (Chat + Vector)");
        report.put("Total Requests", count);
        report.put("Success Requests", latencies.size());
        report.put("Wall Time (ms)", duration);
        report.put("QPS", String.format("%.2f", qps));
        report.put("Avg Latency (ms)", String.format("%.2f", avg));
        report.put("P95 Latency (ms)", p95);
        report.put("P99 Latency (ms)", p99);
        report.put("Max Latency (ms)", max);

        return Result.success(report);
    }
}