package com.example.seckill.goods.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.seckill.common.result.Result;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.common.entity.SpuInfo;
import com.example.seckill.goods.service.GoodsService;
import com.example.seckill.common.vo.GoodsDetailVO;
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
    public Result<String> save(@RequestBody SpuSaveDTO spuSaveDTO, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 架构师批注：必须进行兜底校验，防止 userId 为空
        if (userId == null) {
            // 如果网关漏了，或者直接调接口，给个默认值或报错
            // return Result.error("非法请求：未获取到用户信息");
            userId = 1L; // 暂时兜底，生产环境应报错
        }
        spuSaveDTO.setUserId(userId);
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
    /**
     * 修改商品
     * PUT /goods/update
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody SpuSaveDTO dto) {
        if (dto.getId() == null) {
            return Result.error("商品ID不能为空");
        }
        goodsService.updateGoods(dto);
        return Result.success("修改成功");
    }

    /**
     * 修改上架状态
     * POST /goods/status/{spuId}/{status}
     */
    @PostMapping("/status/{spuId}/{status}")
    public Result<String> updateStatus(@PathVariable Long spuId, @PathVariable Integer status) {
        // 生产环境应校验当前用户是否有权操作该商品 (Check Owner)
        goodsService.updateStatus(spuId, status);
        return Result.success(status == 1 ? "上架成功" : "下架成功");
    }

    /**
     * 查询我的商品列表
     * GET /goods/my-list
     */
    @GetMapping("/my-list")
    public Result<Page<SpuInfo>> myList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (userId == null) return Result.error("未登录");

        Page<SpuInfo> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SpuInfo> query = new LambdaQueryWrapper<>();
        query.eq(SpuInfo::getUserId, userId)
                .orderByDesc(SpuInfo::getCreateTime);

        return Result.success(goodsService.page(pageParam, query));
    }
    /**
     * 删除商品
     * DELETE /goods/delete/{spuId}
     */
    @DeleteMapping("/delete/{spuId}")
    public Result<String> delete(@PathVariable Long spuId) {
        goodsService.removeGoods(spuId);
        return Result.success("删除成功");
    }


    @DeleteMapping("/deleteOne/{spuId}")
    public Result<String> deleteOne(@PathVariable Long spuId) {
        goodsService.deleteOneBySpuID(spuId);
        return Result.success("删除成功");
    }

    /**
     * 【新增】全量同步接口 (后门接口)
     * POST /goods/sync/all
     */
    @PostMapping("/sync/all")
    public Result<String> syncAll() {
        long start = System.currentTimeMillis();
        goodsService.syncAllGoods();
        long end = System.currentTimeMillis();
        return Result.success("全量同步任务已提交，耗时: " + (end - start) + "ms");
    }


}