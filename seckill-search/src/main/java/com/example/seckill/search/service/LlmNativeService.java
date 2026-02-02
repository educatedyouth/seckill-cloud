package com.example.seckill.search.service;

// LlmNativeService.java
public class LlmNativeService {
    // 初始化模型 (加载 GGUF 到显存)
    public native boolean initModel(String modelPath, int gpuLayers, int contextSize, int maxBatchSize);

    // 核心：批量推理接口
    // input: 这是一个字符串数组，比如 ["标题1,简介1", "标题2,简介2"]
    // return: 对应的结果数组
    public native String[] batchInference(String[] prompts);

    // 释放资源
    public native void freeModel();
}