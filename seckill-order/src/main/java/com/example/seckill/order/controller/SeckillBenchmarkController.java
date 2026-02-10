package com.example.seckill.order.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 压测专用 Controller (仅用于生成简历数据)
 * 访问路径: GET /benchmark/run
 */
@Slf4j
@RestController
@RequestMapping("/benchmark")
public class SeckillBenchmarkController {

    // 默认目标地址 (假设服务跑在 8080，请根据实际情况修改)POST http://localhost:8030/seckill/do_seckill
    private static final String TARGET_URL = "http://localhost:8080/seckill/do_seckill";

    // HTTP 客户端 (复用连接)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    /**
     * 执行压测
     * @param threads 并发线程数 (模拟用户数)，默认 1000
     * @param requests 总请求数，默认 10000
     * @param skuId 秒杀商品ID，默认 1001
     * @return 压测报告 JSON
     */
    @GetMapping("/run")
    public Map<String, Object> runBenchmark(
            @RequestParam(defaultValue = "1000") int threads,
            @RequestParam(defaultValue = "10000") int requests,
            @RequestParam(defaultValue = "1001") String skuId
    ) {
        log.info(">>> [压测开始] 并发: {}, 总请求: {}, 商品: {}", threads, requests, skuId);

        // 1. 准备统计容器
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>(requests));

        // 2. 准备线程池与同步锁
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(requests);

        // 3. 生成虚拟 Token (如果你的拦截器需要校验，这里要生成符合格式的 Token)
        // 简单压测可以直接在拦截器放行 benchmark 请求，或者在这里生成 JWT
        String mockTokenPrefix = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjEsInN1YiI6IjEiLCJpYXQiOjE3NzA2OTY5MDIsImV4cCI6MTc3MDc4MzMwMn0.CmmdukgFhsK7yG9-oC2IhEi4iKe58o9zxFowo1RAUTk";

        long startTime = System.currentTimeMillis();

        // 4. 装载任务
        for (int i = 0; i < requests; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // 等待发令枪

                    long reqStart = System.currentTimeMillis();

                    // 发送请求
                    boolean isOk = sendRequest(mockTokenPrefix, skuId);

                    long cost = System.currentTimeMillis() - reqStart;
                    latencies.add(cost);

                    if (isOk) success.incrementAndGet();
                    else fail.incrementAndGet();

                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // 5. 发令枪响
        startGate.countDown();

        // 6. 等待结束
        try {
            endGate.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long totalTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // 7. 计算指标
        return calculateMetrics(totalTime, requests, success.get(), fail.get(), latencies);
    }

    private boolean sendRequest(String token, String skuId) {
        try {
            String jsonBody = "{\"skuId\":\"" + skuId + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TARGET_URL))
                    .header("Content-Type", "application/json")
                    // 如果有网关校验，记得加上 Authorization
                    .header("Authorization", token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> calculateMetrics(long totalTimeMs, int totalReq, int success, int fail, List<Long> latencies) {
        Map<String, Object> report = new LinkedHashMap<>();

        double totalSeconds = totalTimeMs / 1000.0;
        double qps = totalReq / totalSeconds;

        Collections.sort(latencies);
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        long avg = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);

        report.put("1_测试场景", "秒杀下单高并发压测");
        report.put("2_总耗时(秒)", String.format("%.4f", totalSeconds));
        report.put("3_总请求数", totalReq);
        report.put("4_QPS (吞吐量)", String.format("%.2f", qps));
        report.put("5_成功数", success);
        report.put("6_失败数", fail);
        report.put("7_平均耗时(ms)", avg);
        report.put("8_P95耗时(ms)", p95);
        report.put("9_P99耗时(ms)", p99);
        report.put("10_最大耗时(ms)", max);

        log.info(">>> 压测报告: {}", report);
        return report;
    }
}