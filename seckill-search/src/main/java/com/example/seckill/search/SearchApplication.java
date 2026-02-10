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
                        SearchParamDTO searchParamDTO = new SearchParamDTO();
                        searchParamDTO.setKeyword("手表");
                        searchService.searchByGPU(searchParamDTO);
                        long end = System.currentTimeMillis();
                        System.out.println(String.format("=== 线程-%d 完成, 耗时: %dms ===",
                                threadId, (end - start)));

                        // CPU测试代码
                        // SearchParamDTO searchParamDTO = new SearchParamDTO();
                        // searchParamDTO.setKeyword("手表");
                        // searchService.search(searchParamDTO);
                        // long end = System.currentTimeMillis();
                        // System.out.println(String.format("=== 线程-%d 完成, 耗时: %dms ===",
                        //         threadId, (end - start)));

                        // LLM测试代码
                        // String userPrompt = "商品标题：苹果耳机"  + "\n商品简介：顶级音质，蓝牙无线";
                        // // Qwen2.5 标准模版
                        // // String formattedPrompt =
                        // //         "<|im_start|>system\n" +
                        // //                 "你是一个精准的关键词提取工具。请仅输出提取的关键词，用逗号分隔，不要输出任何其他解释、前缀或后缀。\n" +
                        // //                 "<|im_end|>\n" +
                        // //                 "<|im_start|>user\n" +
                        // //                 "提取以下商品的搜索关键词：\n" +
                        // //                 userPrompt + "\n" +
                        // //                 "<|im_end|>\n" +
                        // //                 "<|im_start|>assistant\n"; // 引导它开始回答，不要加内容
                        // // 构造 Prompt (ChatML 格式 + One-Shot 示例 + 发散指令)
                        // String formattedPrompt =
                        //         "<|im_start|>system\n" +
                        //                 "你是一个电商搜索优化专家。请根据商品信息生成5-10个搜索关键词。\n" +
                        //                 "核心要求：\n" +
                        //                 "1. **发散思维**：不要局限于提取原文，必须扩展同义词、场景词、功能词（例如：'iPhone' -> '苹果手机', '送礼', '拍照'）。\n" +
                        //                 "2. **格式严格**：仅输出关键词，用英文逗号分隔，严禁输出任何解释、前缀或后缀。\n" +
                        //                 "<|im_end|>\n" +
                        //                 // --- 示例 (One-Shot) 开始 ---
                        //                 "<|im_start|>user\n" +
                        //                 "商品标题：Nike Air Jordan 1 Low\n" +
                        //                 "商品简介：经典低帮设计，内置气垫，防滑耐磨大底\n" +
                        //                 "<|im_end|>\n" +
                        //                 "<|im_start|>assistant\n" +
                        //                 "耐克,AJ1,篮球鞋,运动板鞋,低帮,情侣鞋,透气,防滑,潮流穿搭\n" +
                        //                 "<|im_end|>\n" +
                        //                 // --- 示例 结束 ---
                        //                 "<|im_start|>user\n" +
                        //                 "提取以下商品的搜索关键词：\n" +
                        //                 userPrompt + "\n" +
                        //                 "<|im_end|>\n" +
                        //                 "<|im_start|>assistant\n"; // 引导模型开始输出
                        // CompletableFuture<LlmBatchService.futureRes> future = llmBatchService.asyncChatWordVec(formattedPrompt);
                        // List<String> result = future.get(300, TimeUnit.SECONDS).futureWord; // 设置个业务超时兜底
                        // float[] resultVec = future.get(300, TimeUnit.SECONDS).futureVec;
                        // System.out.println(result + "\n" + resultVec.length);
                        // long end = System.currentTimeMillis();
                        // System.out.println(String.format("=== 线程-%d 完成, 耗时: %dms ===",
                        //         threadId, (end - start)));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            executor.shutdown();
        }
    }
}