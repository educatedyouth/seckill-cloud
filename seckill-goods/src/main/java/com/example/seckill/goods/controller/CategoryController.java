package com.example.seckill.goods.controller;

import com.example.seckill.common.result.Result;
import com.example.seckill.goods.service.CategoryService;
import com.example.seckill.goods.vo.CategoryTreeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// seckill-goods/src/main/java/com/example/seckill/goods/controller/CategoryController.java
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/list/tree")
    public Result<List<CategoryTreeVO>> listWithTree() {
        return Result.success(categoryService.listWithTree());
    }
}