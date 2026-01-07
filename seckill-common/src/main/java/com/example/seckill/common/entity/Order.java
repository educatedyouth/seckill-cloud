package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 交易订单实体（统一模型）
 * 对应物理表：order_tbl_0 ~ order_tbl_3
 */
@Data
@TableName("order_tbl") // 逻辑表名，MyBatis Plus 默认行为，后续会动态替换
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID (使用基因雪花算法生成)
     */
    @TableId(type = IdType.INPUT) // 注意：我们自己生成ID，不是 Auto Increment
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long skuId;

    /**
     * 购买数量
     */
    private Integer count;

    /**
     * 支付金额
     */
    private BigDecimal money;

    /**
     * 状态：1-新建/待支付, 2-已支付, 3-已发货, 4-已完成, 5-已关闭
     */
    private Integer status;

    /**
     * 订单类型：0-普通订单, 1-秒杀订单
     */
    private Integer orderType;

    private Date createTime;

    private Date updateTime;
}