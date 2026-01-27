package com.example.seckill.search.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.search.dto.SearchParamDTO;
import com.example.seckill.search.service.SearchService;
import com.example.seckill.search.vo.SearchResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private SearchService searchService;

    /**
     * 商品搜索接口 (前端首页调用)
     * POST /search/list
     */
    @PostMapping("/list")
    public Result<SearchResultVO> list(@RequestBody SearchParamDTO param) {
        // 调用我们写好的核心搜索逻辑 (带高亮、聚合等)
        SearchResultVO result = searchService.searchByGPU(param);
        return Result.success(result);
    }
}