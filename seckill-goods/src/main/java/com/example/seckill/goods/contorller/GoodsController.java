package com.example.seckill.goods.contorller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.seckill.common.result.Result;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.goods.entity.SpuInfo;
import com.example.seckill.goods.service.GoodsService;
import com.example.seckill.goods.vo.GoodsDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 获取商品详情
     * GET /goods/detail/{spuId}
     */
    @GetMapping("/detail/{spuId}")
    public Result<GoodsDetailVO> getDetail(@PathVariable("spuId") Long spuId) {
        GoodsDetailVO vo = goodsService.getGoodsDetail(spuId);
        if (vo == null) {
            return Result.error("商品不存在");
        }
        return Result.success(vo);
    }
    /**
     * 获取商品分页详情
     * GET /goods/list/
     */
    @GetMapping("/list")
    public Result<Page<SpuInfo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        // 1. 构造分页参数
        Page<SpuInfo> pageParam = new Page<>(page, size);

        // 2. 构造查询条件：必须是"上架"状态 (publish_status = 1)，且按时间倒序
        LambdaQueryWrapper<SpuInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SpuInfo::getPublishStatus, 1)
                .orderByDesc(SpuInfo::getCreateTime);

        // 3. 执行查询 (直接调用 Service 的 page 方法，MyBatis Plus 提供的能力)
        Page<SpuInfo> result = goodsService.page(pageParam, queryWrapper);

        return Result.success(result);
    }
}