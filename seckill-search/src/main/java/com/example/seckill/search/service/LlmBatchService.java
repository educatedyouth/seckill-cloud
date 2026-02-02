package com.example.seckill.search.service;

import com.example.seckill.search.service.llm.LlmRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@Slf4j
public class LlmBatchService {

    // ================= 配置参数 =================
    private static final int BATCH_SIZE_THRESHOLD = 24; // 批次阈值
    private static final int TIME_THRESHOLD_MS = 50;    // 时间阈值 (毫秒)
    private static final int MAX_QUEUE_SIZE = 10000;     // 队列最大积压数，防OOM

    // 线程安全的请求队列
    private final BlockingQueue<LlmRequest> requestQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    // 后台调度线程
    private final Thread schedulerThread;
    private volatile boolean running = true;

    public LlmBatchService() {
        this.schedulerThread = new Thread(this::batchLoop, "llm-batch-scheduler");
    }

    // ================= Native JNI 接口 =================
    // 这里的 prompts 数组长度就是当前的 batchSize
    // 返回值是对应顺序的结果数组
    public native String[] batchInference(String[] prompts);
    public native boolean initModel(String modelPath, int gpuLayers, int contextSize, int maxBatchSize);
    // ================= 业务调用入口 =================
    /**
     * 外部业务调用此方法，立即获得一个 Future，不会阻塞
     */
    public CompletableFuture<String> asyncChat(String prompt) {
        LlmRequest request = new LlmRequest(prompt);
        boolean success = requestQueue.offer(request);
        if (!success) {
            // 队列满了（比如瞬间来了1万个请求），直接快速失败，或者走降级逻辑
            request.getFuture().completeExceptionally(new RejectedExecutionException("LLM Queue Full"));
        }
        return request.getFuture();
    }

    // ================= 核心调度循环 =================
    private void batchLoop() {
        while (running) {
            try {
                // 1. 阻塞等待第一个请求到来 (这是关键，没有请求时线程挂起，不费CPU)
                LlmRequest firstReq = requestQueue.take();

                List<LlmRequest> currentBatch = new ArrayList<>(BATCH_SIZE_THRESHOLD);
                currentBatch.add(firstReq);

                // 2. 设定截止时间：从收到第一个请求开始计时 50ms
                long deadline = System.currentTimeMillis() + TIME_THRESHOLD_MS;

                // 3. 贪婪获取后续请求，直到满32个 或 时间到
                while (currentBatch.size() < BATCH_SIZE_THRESHOLD) {
                    long remainingTime = deadline - System.currentTimeMillis();
                    if (remainingTime <= 0) {
                        break; // 时间到了，立即发车
                    }

                    // 尝试在剩余时间内 poll 一个请求
                    // 注意：poll 不会死等，超时就返回 null
                    LlmRequest nextReq = requestQueue.poll(remainingTime, TimeUnit.MILLISECONDS);
                    if (nextReq == null) {
                        break; // 时间到了还没等到新的，发车
                    }
                    currentBatch.add(nextReq);
                }

                // 4. 执行批量推理
                if (!currentBatch.isEmpty()) {
                    processBatch(currentBatch);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Batch Loop Error", e);
            }
        }
    }

    private void processBatch(List<LlmRequest> batch) {
        // 1. 提取所有 Prompt
        String[] inputs = batch.stream()
                .map(LlmRequest::getPrompt)
                .toArray(String[]::new);

        log.info(">>> [LLM Batch] 发车! Size: {}", batch.size());

        try {
            // 2. JNI 调用 C++ 推理 (耗时操作)
            // 注意：此时 C++ 端已经修改为支持 2048 长度，R1 会输出完整的思考过程
            String[] outputs = batchInference(inputs);

            // 3. 结果清洗与分发
            if (outputs != null && outputs.length == batch.size()) {
                for (int i = 0; i < batch.size(); i++) {
                    String rawOutput = outputs[i];
                    String cleanResult = rawOutput;
                    // 情况A：完整的 <think>...</think> -> 正则替换
                    if (rawOutput.contains("<think>") && rawOutput.contains("</think>")) {
                        cleanResult = rawOutput.replaceAll("<think>[\\s\\S]*?</think>", "");
                    }
                    // 情况B：只有尾巴 </think> (因为开头被截断或模型没输出开头) -> 截取后半段
                    else if (rawOutput.contains("</think>")) {
                        int index = rawOutput.indexOf("</think>");
                        cleanResult = rawOutput.substring(index + 8); // 8 是 "</think>".length()
                    }
                    // 情况C：只有开头 <think> (长度不够被截断) -> 只要前面的，或者提示错误
                    else if (rawOutput.contains("<think>")) {
                        cleanResult = ""; // 没想完，通常结果不可用
                    }

                    batch.get(i).getFuture().complete(cleanResult);
                }
            } else {
                throw new RuntimeException("C++ returned size mismatch or null");
            }

        } catch (Throwable t) {
            for (LlmRequest req : batch) {
                req.getFuture().completeExceptionally(t);
            }
            log.error(">>> [LLM Batch] Native Inference Failed", t);
        }
    }

    @PostConstruct
    public void init() {
        // 加载 DLL
        String libPath = "D:\\JavaTest\\seckill-cloud\\seckill-search\\src\\main\\resources\\native-libs\\";
        // 加载 CUDA Runtime
        System.load(libPath + "SeckillLlmService.dll");
        System.out.println(">>> [JNI] SeckillLlmService 加载成功");
        // 启动后台线程
        String modelPath = "D:\\Hzj76\\Downloads\\DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf";
        boolean success = initModel(modelPath, -1, 4096 * 4, BATCH_SIZE_THRESHOLD);

        if (success) {
            log.info(">>> [LLM Service] 模型加载成功! GPU Layers: {}", -1);
            schedulerThread.start();
        } else {
            log.error(">>> [LLM Service] 模型加载失败，请检查路径或显存!");
            // 可以选择抛出异常阻止 Spring 启动
            // throw new RuntimeException("Model Init Failed");
        }
    }

    @PreDestroy
    public void destroy() {
        running = false;
        schedulerThread.interrupt();
    }
}