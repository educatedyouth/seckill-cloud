package com.example.seckill.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope; // 【关键】引入热更新注解
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Task 4.1: AI Infrastructure Service
 * 手动封装 Ollama 接口调用，提供关键词扩充与向量化能力
 */
@Service
@Slf4j
@RefreshScope // 【核心】开启 Nacos 配置热更新支持
public class LlmService {

    private final RestTemplate restTemplate = createRestTemplate();

    // 2. 在类里面添加这个私有方法
    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10 * 1000); // 连接超时：10秒
        factory.setReadTimeout(120 * 1000);   // 读取超时：120秒 (关键是这个，设长一点)
        return new RestTemplate(factory);
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.host}")
    private String ollamaHost;

    @Value("${llm.chat-model}")
    private String modelChat;

    @Value("${llm.embed-model}")
    private String modelEmbed;

    // 【核心】注入 Nacos 中的 Prompt 模板
    // 冒号后面是默认值，防止 Nacos 没配时报错
    @Value("${llm.prompt.expand-keywords:你是一个电商搜索优化专家。请根据以下商品信息，提取并生成关键词。商品标题：%s\n商品简介：%s}")
    private String expandKeywordsPrompt;

    /**
     * 核心方法 1：扩充关键词
     * 使用 DeepSeek 推理能力，根据标题和简介生成同义词、场景词等
     */
    public List<String> expandKeywords(String title, String desc) {
        // 1. Prompt Engineering (Dynamic)
        // 使用配置中心的模板进行格式化
        String prompt = String.format(expandKeywordsPrompt, title, desc);

        // 调试日志：打印当前使用的 Prompt，方便验证热更新是否生效
        log.info(">>> [AI] 当前 Prompt 模板长度: {}, 内容片段: {}", expandKeywordsPrompt.length(), expandKeywordsPrompt.substring(0, Math.min(20, expandKeywordsPrompt.length())) + "...");

        Map<String, Object> request = new HashMap<>();
        request.put("model", modelChat);
        request.put("prompt", prompt);
        request.put("stream", false);

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.5); // 温度保持 0.5 较为稳定
        request.put("options", options);

        try {
            long start = System.currentTimeMillis();
            String url = ollamaHost + "/api/generate";
            String response = restTemplate.postForObject(url, request, String.class);
            long end = System.currentTimeMillis();

            // 解析 Ollama 响应
            JsonNode root = objectMapper.readTree(response);
            String responseText = root.path("response").asText();

            // 清洗数据：DeepSeek R1 可能会输出思考过程 <think>...</think>
            if (responseText.contains("</think>")) {
                responseText = responseText.substring(responseText.indexOf("</think>") + 8);
            }
            responseText = responseText.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
            // 标点归一化
            responseText = responseText.replace("\n", ",").replace("，", ",").replace("。", "");

            String[] splits = responseText.split(",");
            List<String> keywords = new ArrayList<>();
            for (String s : splits) {
                String k = s.trim();
                if (k.length() > 1) {
                    keywords.add(k);
                }
            }

            log.info(">>> [AI] 关键词扩充成功 ({}ms) | 原词: {} | 扩充: {}", (end - start), title, keywords);
            return keywords;

        } catch (Exception e) {
            log.error(">>> [AI] 关键词扩充失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 核心方法 2：获取文本向量 (保持不变)
     */
    public List<Float> getVector(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", modelEmbed);
        request.put("prompt", text);

        try {
            String url = ollamaHost + "/api/embeddings";
            String response = restTemplate.postForObject(url, request, String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode embeddingNode = root.path("embedding");

            List<Float> vector = new ArrayList<>();
            if (embeddingNode.isArray()) {
                for (JsonNode node : embeddingNode) {
                    vector.add((float) node.asDouble());
                }
            }

            if (!vector.isEmpty() && vector.size() != 1024) {
                log.warn(">>> [AI] 向量维度异常，期望 1024，实际 {}", vector.size());
            }

            return vector;

        } catch (Exception e) {
            log.error(">>> [AI] 向量生成失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}