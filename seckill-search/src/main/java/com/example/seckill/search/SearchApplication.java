package com.example.seckill.search;

import com.example.seckill.search.dto.SearchParamDTO;
import com.example.seckill.search.service.GpuSearchService;
import com.example.seckill.search.service.LlmBatchService;
import com.example.seckill.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;

@SpringBootApplication(scanBasePackages = {"com.example.seckill"}) // 扫描common包
@EnableDiscoveryClient
@EnableFeignClients
public class SearchApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
    @Component
    public static class ConcurrentSearchRunner implements CommandLineRunner {

        @Autowired
        private GpuSearchService gpuSearchService;

        private final SearchService searchService;
        private final LlmBatchService llmBatchService ;
        public ConcurrentSearchRunner(SearchService myService,LlmBatchService llmBatchService) {
            this.searchService = myService;
            this.llmBatchService = llmBatchService;
        }

        @Override
        public void run(String... args) throws Exception {
            // 等待服务启动和预热完成
            System.out.println(">>> 等待 0.5秒 确保预热完成...");
            Thread.sleep(500);

            int threads = 100; // 并发数，对应你的 C++ 资源池大小
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            // 这是一个"发令枪"，确保 3 个线程同时起跑
            CountDownLatch latch = new CountDownLatch(threads);

            System.out.println(">>> [Test] 准备发起 3 路并发搜索...");

            for (int i = 0; i < threads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        String keyword = "测试并发商品_" + threadId;

                        // 1. 准备就绪，扣动扳机
                        latch.countDown();
                        latch.await();
                        // 2. 只有当 3 个线程都执行了 countDown，这里才会同时释放
                        // (实际上这里 latch 的用法应该是 await，但为了简单，
                        // 我们直接用 invokeAll 或者简单的 sleep 模拟也可以，
                        // 下面这种写法是简化的并发触发)

                        long start = System.currentTimeMillis();

                        // 3. 调用search接口，可GPU可CPU，用来测试GPU速度
                        // GPU测试代码
                        // SearchParamDTO searchParamDTO = new SearchParamDTO();
                        // searchParamDTO.setKeyword("手表");
                        // searchService.searchByGPU(searchParamDTO);
                        // long end = System.currentTimeMillis();
                        // System.out.println(String.format("=== 线程-%d 完成, 耗时: %dms ===",
                        //         threadId, (end - start)));

                        // CPU测试代码
                        // SearchParamDTO searchParamDTO = new SearchParamDTO();
                        // searchParamDTO.setKeyword("手表");
                        // searchService.search(searchParamDTO);
                        // long end = System.currentTimeMillis();
                        // System.out.println(String.format("=== 线程-%d 完成, 耗时: %dms ===",
                        //         threadId, (end - start)));

                        // LLM测试代码
                        String userPrompt = "商品标题：苹果耳机"+"\n商品简介：airpods pro 2代，非凡音质";
                        String formattedPrompt =
                                        "你是一个电商搜索优化专家。请根据商品标题和简介，扩展生成一行5-10个中文搜索关键词，不要分段，不要编号，关键词之间用逗号分隔，要求包含同义词，功能词，场景词等简短词汇。\n" +
                                        userPrompt;
                        CompletableFuture<LlmBatchService.futureRes> future = llmBatchService.asyncChatWordVec(formattedPrompt);
                        List<String> result = future.get(300, TimeUnit.SECONDS).futureWord; // 设置个业务超时兜底
                        float[] resultVec = future.get(300, TimeUnit.SECONDS).futureVec;
                        System.out.println(result + "\n" + resultVec.length);
                        long end = System.currentTimeMillis();
                        System.out.println(String.format("=== 线程-%d 完成, 耗时: %dms ===",
                                threadId, (end - start)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            executor.shutdown();
        }
    }
}