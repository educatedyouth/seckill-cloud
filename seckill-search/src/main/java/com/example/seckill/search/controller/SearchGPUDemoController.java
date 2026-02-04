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

import java.util.*;
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

        int k = 10;
        long[] topIds = gpuSearchService.search(keyword, k);
        List<Long> idList = Arrays.stream(topIds).boxed().collect(Collectors.toList());
        // 2. 从数据库批量查出无序的商品列表
        Iterable<GoodsDoc> unorderedDocs = goodsRepository.findAllById(idList);
        Map<Long, GoodsDoc> docMap = new HashMap<>();
        unorderedDocs.forEach(doc -> docMap.put(doc.getId(), doc));

        // 3. 【关键步骤】按照 GPU 返回的 idList 顺序，重新组装结果列表
        List<GoodsDoc> sortedDocs = new ArrayList<>();
        for (Long id : idList) {
            if (docMap.containsKey(id)) {
                sortedDocs.add(docMap.get(id));
            }
        }

        // 4. 打印 (现在顺序绝对和 GPU 一致了)
        consoleLog.append("------ 检索结果 (Top ").append(k).append(") ------\n");
        for (GoodsDoc doc : sortedDocs) {
            consoleLog.append(String.format("[ID: %d] ￥%.2f - %s\n",
                    doc.getId(), doc.getPrice(), doc.getTitle()));
        }
        System.out.println(consoleLog.toString());

        return consoleLog.toString().replace("\n", "<br/>");
    }

    @GetMapping("/normAll")
    public void normAll(){
        System.out.println("Begin Normed All");
        Iterable<GoodsDoc> allDocs = goodsRepository.findAll();
        List<GoodsDoc> sourceList = new ArrayList<>();
        allDocs.forEach(sourceList::add);
        for(var tmp:sourceList){
            // 2. 原始类型循环计算
            float sum = 0.0f;
            for (float v : tmp.getEmbeddingVector()) {
                sum += v * v;
            }
            float norm = (float) Math.sqrt(sum);
            List<Float>normedVec = new ArrayList<>();
            for(int i = 0; i < tmp.getEmbeddingVector().size();i++){
                normedVec.add(tmp.getEmbeddingVector().get(i)/norm);
            }
            tmp.setEmbeddingVector(normedVec);
            goodsRepository.save(tmp);
        }
    }
}