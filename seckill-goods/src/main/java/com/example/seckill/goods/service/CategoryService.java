package com.example.seckill.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckill.goods.entity.Category;
import com.example.seckill.goods.vo.CategoryTreeVO;

import java.util.List;

public interface CategoryService extends IService<Category> {
    // 【核心接口】查出所有分类，并组装成父子树形结构
    List<CategoryTreeVO> listWithTree();
}