// seckill-goods/src/main/java/com/example/seckill/goods/service/impl/GoodsServiceImpl.java
package com.example.seckill.goods.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.goods.dto.SkuSaveDTO;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.goods.entity.*;
import com.example.seckill.goods.mapper.*;
import com.example.seckill.goods.service.GoodsService;
import com.example.seckill.goods.vo.GoodsDetailVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GoodsServiceImpl extends ServiceImpl<SpuInfoMapper, SpuInfo> implements GoodsService {

    @Autowired
    private SkuInfoMapper skuInfoMapper;
    @Autowired
    private SkuImagesMapper skuImagesMapper;
    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Transactional(rollbackFor = Exception.class) // 【关键】开启事务，任何一步失败全部回滚
    @Override
    public void saveGoods(SpuSaveDTO dto) {
        // 1. 保存 SPU 基本信息 (pms_spu_info)
        SpuInfo spuInfo = new SpuInfo();
        BeanUtils.copyProperties(dto, spuInfo);
        spuInfo.setCreateTime(new Date());
        spuInfo.setUpdateTime(new Date());
        // baseMapper 就是 SpuInfoMapper
        this.baseMapper.insert(spuInfo);

        // 获取刚刚保存的 SPU ID，后续 SKU 都要关联这个 ID
        Long spuId = spuInfo.getId();

        // 2. 保存 SKU 列表
        List<SkuSaveDTO> skus = dto.getSkus();
        if (skus != null && !skus.isEmpty()) {
            for (SkuSaveDTO skuDTO : skus) {
                // 2.1 保存 SKU 基本信息 (pms_sku_info)
                SkuInfo skuInfo = new SkuInfo();
                BeanUtils.copyProperties(skuDTO, skuInfo);
                skuInfo.setSpuId(spuId); // 关联父ID
                skuInfo.setCategoryId(spuInfo.getCategoryId());
                skuInfo.setBrandId(spuInfo.getBrandId());
                skuInfo.setSaleCount(0L);
                // 设置默认图 (如果前端传了 defaultImg 就用，没传就拿图片列表第一张)
                String defaultImg = skuDTO.getDefaultImg();
                if (defaultImg == null && skuDTO.getImages() != null && !skuDTO.getImages().isEmpty()) {
                    defaultImg = skuDTO.getImages().get(0);
                }
                skuInfo.setSkuDefaultImg(defaultImg);

                skuInfoMapper.insert(skuInfo);
                Long skuId = skuInfo.getSkuId();

                // 2.2 保存 SKU 图片墙 (pms_sku_images)
                List<String> images = skuDTO.getImages();
                if (images != null) {
                    List<SkuImages> skuImagesList = images.stream().map(img -> {
                        SkuImages skuImages = new SkuImages();
                        skuImages.setSkuId(skuId);
                        skuImages.setImgUrl(img);
                        skuImages.setDefaultImg(img.equals(skuInfo.getSkuDefaultImg()) ? 1 : 0);
                        return skuImages;
                    }).collect(Collectors.toList());

                    // 循环插入 (MyBatis-Plus 批量插入需要额外配置，这里先用循环，简单直观)
                    for (SkuImages img : skuImagesList) {
                        skuImagesMapper.insert(img);
                    }
                }

                // 2.3 保存 SKU 销售属性 (pms_sku_sale_attr_value)
                // 例如：颜色-红色，内存-128G
                if (skuDTO.getSaleAttrs() != null) {
                    List<SkuSaleAttrValue> attrValues = skuDTO.getSaleAttrs().stream().map(attr -> {
                        SkuSaleAttrValue val = new SkuSaleAttrValue();
                        BeanUtils.copyProperties(attr, val);
                        val.setSkuId(skuId);
                        return val;
                    }).collect(Collectors.toList());

                    for (SkuSaleAttrValue val : attrValues) {
                        skuSaleAttrValueMapper.insert(val);
                    }
                }
            }
        }
    }
    @Override
    public GoodsDetailVO getGoodsDetail(Long spuId) {
        // 1. 查询 SPU 主表
        SpuInfo spuInfo = this.getById(spuId);
        if (spuInfo == null) {
            return null; // 或者抛出业务异常
        }

        // 生产级校验：如果做的是C端接口，这里必须判断上下架状态
        // if (spuInfo.getPublishStatus() != 1) { throw ... }

        // 2. 查询该 SPU 下所有的 SKU
        LambdaQueryWrapper<SkuInfo> skuQuery = new LambdaQueryWrapper<>();
        skuQuery.eq(SkuInfo::getSpuId, spuId);
        List<SkuInfo> rawSkuList = skuInfoMapper.selectList(skuQuery);

        if (rawSkuList == null || rawSkuList.isEmpty()) {
            // 构造仅有 SPU 的空数据返回，防止空指针
            GoodsDetailVO vo = new GoodsDetailVO();
            vo.setSpuInfo(spuInfo);
            return vo;
        }

        // 3. 收集所有的 skuId，准备批量查询属性 (避免在循环中查库，这是性能杀手)
        List<Long> skuIds = rawSkuList.stream()
                .map(SkuInfo::getSkuId)
                .collect(Collectors.toList());

        // 4. 批量查询所有相关 SKU 的销售属性 (pms_sku_sale_attr_value)
        LambdaQueryWrapper<SkuSaleAttrValue> attrQuery = new LambdaQueryWrapper<>();
        attrQuery.in(SkuSaleAttrValue::getSkuId, skuIds);
        // 按排序字段排序，保证前端展示顺序一致
        attrQuery.orderByAsc(SkuSaleAttrValue::getAttrSort);
        List<SkuSaleAttrValue> allAttrValues = skuSaleAttrValueMapper.selectList(attrQuery);

        // 5. 内存聚合 (Memory Aggregation)
        // 将扁平的属性列表，按 skuId 分组
        Map<Long, List<SkuSaleAttrValue>> attrGroupMap = allAttrValues.stream()
                .collect(Collectors.groupingBy(SkuSaleAttrValue::getSkuId));

        // 6. 组装最终的 SKUItem 列表
        List<GoodsDetailVO.SkuItem> skuItems = rawSkuList.stream().map(sku -> {
            GoodsDetailVO.SkuItem item = new GoodsDetailVO.SkuItem();
            // 复制 SKU 基础属性
            BeanUtils.copyProperties(sku, item);
            // 设置对应的属性列表
            item.setSaleAttrValues(attrGroupMap.get(sku.getSkuId()));
            return item;
        }).collect(Collectors.toList());

        // 7. 封装最终 VO
        GoodsDetailVO vo = new GoodsDetailVO();
        vo.setSpuInfo(spuInfo);
        vo.setSkuList(skuItems);

        return vo;
    }
}