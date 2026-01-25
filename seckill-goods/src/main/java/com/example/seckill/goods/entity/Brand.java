package com.example.seckill.goods.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("pms_brand")
public class Brand implements Serializable {
    private static final long serialVersionUID = 1L;
    // 【修改】指定类型为 AUTO，代表跟随数据库自增
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String logo;
    private Integer showStatus;
    private String firstLetter;
    private Integer sort;
}