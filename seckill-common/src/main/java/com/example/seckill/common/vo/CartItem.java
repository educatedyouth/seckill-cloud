package com.example.seckill.common.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物项（存储在 Redis Hash 的 Value ）
 */
@Data
public class CartItem {
    private Long skuId;
    private Long spuId; // 用于点击跳转商品详情
    private String title;
    private String image; // 商品默认图片
    private List<String> skuAttr; // 商品规格属性 ["红色", "128G"]
    private BigDecimal price; // 加入购物车时的价格
    private Integer count; // 购买数量
    private BigDecimal totalPrice; //不仅仅是单价*数量，可能后续有活动计算
    // 【新增】当前商品真实库存（用于前端限制最大购买数）
    private Integer stock;
    // 计算小计金额
    public BigDecimal getTotalPrice() {
        if (this.price != null && this.count != null) {
            return this.price.multiply(new BigDecimal("" + this.count));
        }
        return BigDecimal.ZERO;
    }
}