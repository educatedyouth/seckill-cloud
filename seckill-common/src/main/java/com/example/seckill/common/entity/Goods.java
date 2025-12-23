package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("goods_tbl") // 假设你以后会有这张表
public class Goods {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String goodsName;
    private BigDecimal price;
    private Integer stock;
}