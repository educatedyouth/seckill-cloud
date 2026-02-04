package com.example.seckill.search.service.llm;

import lombok.Data;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Data
public class LlmRequest {
    private String prompt;
    private CompletableFuture<List<String>> futureWord;
    private CompletableFuture<float[]> futureVec;
    public LlmRequest(String prompt) {
        this.prompt = prompt;
        this.futureWord = new CompletableFuture<>();
        this.futureVec = new CompletableFuture<>();
    }
}