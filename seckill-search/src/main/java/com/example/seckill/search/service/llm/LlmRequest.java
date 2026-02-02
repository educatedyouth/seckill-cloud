package com.example.seckill.search.service.llm;

import lombok.Data;
import java.util.concurrent.CompletableFuture;

@Data
public class LlmRequest {
    private String prompt;
    // 用于通知业务层结果的“邮筒”
    private CompletableFuture<String> future;

    public LlmRequest(String prompt) {
        this.prompt = prompt;
        this.future = new CompletableFuture<>();
    }
}