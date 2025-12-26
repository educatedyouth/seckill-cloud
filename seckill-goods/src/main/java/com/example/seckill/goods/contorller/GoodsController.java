package com.example.seckill.goods.contorller;

import com.example.seckill.common.result.Result;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.goods.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// seckill-goods/src/main/java/com/example/seckill/goods/controller/GoodsController.java
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    /**
     * 商品发布接口
     * POST /goods/save
     */
    @PostMapping("/save")
    public Result<String> save(@RequestBody SpuSaveDTO spuSaveDTO) {
        goodsService.saveGoods(spuSaveDTO);
        return Result.success("商品发布成功");
    }
}