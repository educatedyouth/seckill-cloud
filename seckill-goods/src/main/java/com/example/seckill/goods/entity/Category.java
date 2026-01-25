package com.example.seckill.goods.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
@TableName("pms_category")
public class Category implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;
    private String name;
    private Long parentId;
    private Integer catLevel; // 层级 1,2,3
    private Integer showStatus; // 1显示 0不显示
    private Integer sort;
    private String icon;
    private String productUnit;

    // 【关键】这个字段数据库没有，是给后端递归组装树形结构用的
    @TableField(exist = false)
    private List<Category> children;
}