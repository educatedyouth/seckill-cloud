package com.example.seckill.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Task 4.1: AI Infrastructure Service
 * 手动封装 Ollama 接口调用，提供关键词扩充与向量化能力
 */
@Service
@Slf4j
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.host}")
    private String ollamaHost;

    @Value("${llm.chat-model}")
    private String modelChat;

    @Value("${llm.embed-model}")
    private String modelEmbed;

    /**
     * 核心方法 1：扩充关键词
     * 使用 DeepSeek 推理能力，根据标题和简介生成同义词、场景词等
     */
    public List<String> expandKeywords(String title, String desc) {
        // 1. Prompt Engineering
        // 强制要求 JSON 格式或纯文本逗号分隔，这里使用纯文本逗号分隔更稳定
        String prompt = String.format(
                "你是一个电商搜索优化专家。请根据以下商品信息，提取并生成 5-10 个搜索关键词。\n" +
                        "要求：\n" +
                        "1. 包含核心词、同义词、竞品词、常见错别字、功能场景词。\n" +
                        "2. 直接输出纯文本，用英文逗号分隔，不要包含任何解释、序号、前缀或后缀。\n" +
                        "3. 不要输出 <think> 标签的内容。\n" +
                        "\n" +
                        "商品标题：%s\n" +
                        "商品简介：%s",
                title, desc
        );

        Map<String, Object> request = new HashMap<>();
        request.put("model", modelChat);
        request.put("prompt", prompt);
        request.put("stream", false);
        // 降低温度，让结果更确定
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.5);
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
            // 再次兜底清洗
            responseText = responseText.replaceAll("<think>[\\s\\S]*?</think>", "").trim();
            // 标点归一化
            responseText = responseText.replace("\n", ",").replace("，", ",").replace("。", "");

            String[] splits = responseText.split(",");
            List<String> keywords = new ArrayList<>();
            for (String s : splits) {
                String k = s.trim();
                if (k.length() > 1) { // 过滤无效短词
                    keywords.add(k);
                }
            }

            log.info(">>> [AI] 关键词扩充成功 ({}ms) | 原词: {} | 扩充: {}", (end - start), title, keywords);
            return keywords;

        } catch (Exception e) {
            log.error(">>> [AI] 关键词扩充失败: {}", e.getMessage());
            return new ArrayList<>(); // 降级策略：返回空列表
        }
    }

    /**
     * 核心方法 2：获取文本向量
     * 使用 bge-m3 模型将文本转为向量
     * 注意：严格返回 List<Float>
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
                    // 强转为 Float，节省存储空间并匹配 ES dense_vector 类型
                    vector.add((float) node.asDouble());
                }
            }

            // 简单校验维度 (BGE-M3 应该是 1024)
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