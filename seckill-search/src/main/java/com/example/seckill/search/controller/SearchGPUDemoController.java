package com.example.seckill.search.controller;

import com.example.seckill.search.entity.GoodsDoc;
import com.example.seckill.search.repository.GoodsRepository;
import com.example.seckill.search.service.GpuSearchService;
import com.example.seckill.search.service.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@RestController
@RequestMapping("/search/gpu")
public class SearchGPUDemoController {

    @Autowired
    private LlmService llmService;

    @Autowired
    private GpuSearchService gpuSearchService;

    @Autowired
    private GoodsRepository goodsRepository;

    /**
     * 极速 GPU 检索 Demo
     * URL: http://localhost:8080/search/gpu/demo?keyword=高性能笔记本
     */
    @GetMapping("/demo")
    public String demoSearch(@RequestParam("keyword") String keyword) {
        StringBuilder consoleLog = new StringBuilder();
        consoleLog.append("====== GPU 极速检索演示 ======\n");
        consoleLog.append("Query: ").append(keyword).append("\n");

        long t1 = System.currentTimeMillis();

        // 1. 文本 -> 向量 (LLM)
        List<Float> queryVec = llmService.getVector(keyword);
        if (queryVec == null || queryVec.size() != 1024) {
            return "向量生成失败";
        }

        // List 转 float[]
        float[] queryArray = new float[1024];
        for (int i = 0; i < 1024; i++) queryArray[i] = queryVec.get(i);

        long t2 = System.currentTimeMillis();
        consoleLog.append("Step 1: 向量化耗时: ").append(t2 - t1).append("ms\n");

        // 2. 向量 -> TopK IDs (GPU)
        int k = 10;
        long[] topIds = gpuSearchService.searchTopK(queryArray, k);

        long t3 = System.currentTimeMillis();
        consoleLog.append("Step 2: GPU 计算耗时: ").append(t3 - t2).append("ms (Search + Sort)\n");

        // 3. IDs -> 商品详情 (MySQL/ES)
        consoleLog.append("------ 检索结果 (Top ").append(k).append(") ------\n");

        // 转换 IDs 为 List
        List<Long> idList = LongStream.of(topIds).boxed().collect(Collectors.toList());
        Iterable<GoodsDoc> docs = goodsRepository.findAllById(idList);

        // 简单打印结果
        for (GoodsDoc doc : docs) {
            consoleLog.append(String.format("[ID: %d] ￥%.2f - %s\n",
                    doc.getId(), doc.getPrice(), doc.getTitle()));
        }

        // 在服务器控制台也打印一遍，方便你调试
        System.out.println(consoleLog.toString());

        return consoleLog.toString().replace("\n", "<br/>");
    }
}