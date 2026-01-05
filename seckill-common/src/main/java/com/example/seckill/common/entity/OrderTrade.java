package com.example.seckill.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 交易订单核心实体 (重构版)
 * 采用 Long 类型 ID，防止雪花算法精度丢失
 * 包含完整的金额、用户信息、收货快照
 */
@Data
@TableName("seckill_trade_order") // 对应数据库新表
public class OrderTrade implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订单ID (使用雪花算法生成，Long类型)
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID (SKU ID)
     */
    private Long skuId;

    /**
     * 购买数量
     */
    private Integer count;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 应付金额 (实际支付金额)
     */
    private BigDecimal payAmount;

    /**
     * 运费
     */
    private BigDecimal freightAmount;

    /**
     * 订单状态: 0->待付款；1->待发货；2->已发货；3->已完成；4->已关闭；5->无效订单
     */
    private Integer status;

    /**
     * 订单类型：0->正常订单；1->秒杀订单
     */
    private Integer orderType;

    /**
     * 物流单号
     */
    private String deliverySn;

    /**
     * 收货人姓名 (快照)
     */
    private String receiverName;

    /**
     * 收货人电话 (快照)
     */
    private String receiverPhone;

    /**
     * 收货人详细地址 (快照)
     */
    private String receiverDetailAddress;

    /**
     * 订单备注
     */
    private String note;

    /**
     * 支付时间
     */
    private Date paymentTime;

    /**
     * 发货时间
     */
    private Date deliveryTime;

    /**
     * 确认收货时间
     */
    private Date receiveTime;

    /**
     * 提交时间
     */
    private Date createTime;

    /**
     * 修改时间
     */
    private Date modifyTime;
}