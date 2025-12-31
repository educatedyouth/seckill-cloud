package com.example.seckill.search.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.search.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search/test")
public class SearchTestController {

    @Autowired
    private SearchService searchService;

    /**
     * 手动触发上架同步
     * URL: http://localhost:8050/search/test/up/1
     */
    @GetMapping("/up/{spuId}")
    public Result<String> testUp(@PathVariable Long spuId) {
        boolean success = searchService.syncUp(spuId);
        return success ? Result.success("同步成功") : Result.error("同步失败");
    }
}