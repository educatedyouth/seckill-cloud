package com.example.seckill.search.repository;

import com.example.seckill.search.entity.GoodsDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * ES 操作仓库
 * 继承 ElasticsearchRepository<实体类型, ID类型>
 * * 作用：
 * 1. 提供 save(), delete(), findById() 等基础方法
 * 2. 提供 search() 方法配合 DSL 使用
 */
@Repository
public interface GoodsRepository extends ElasticsearchRepository<GoodsDoc, Long> {
    // 暂时不需要自定义方法，基础方法已够用
}