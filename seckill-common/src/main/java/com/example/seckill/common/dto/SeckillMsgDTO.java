package com.example.seckill.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMsgDTO implements Serializable {
    private Integer userId;
    private Integer goodsId;
    // 后面如果要加秒杀价格、场次ID，直接在这里加字段，不用改解析逻辑
}