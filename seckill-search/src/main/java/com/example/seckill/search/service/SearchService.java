package com.example.seckill.search.service;

import com.example.seckill.common.result.Result;
import com.example.seckill.common.vo.GoodsDetailVO;
import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.entity.SpuInfo;
import com.example.seckill.search.entity.GoodsDoc;
import com.example.seckill.search.feign.GoodsFeignClient;
import com.example.seckill.search.repository.GoodsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class SearchService {

    @Autowired
    private GoodsFeignClient goodsFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;

    /**
     * 上架商品同步 (MySQL -> ES)
     * @param spuId 商品ID
     * @return 是否成功
     */
    public boolean syncUp(Long spuId) {
        // 1. 远程调用获取商品详情
        Result<GoodsDetailVO> result = goodsFeignClient.getGoodsDetail(spuId);
        if (result == null || result.getData() == null) {
            log.error("同步失败：无法获取商品信息 spuId={}", spuId);
            return false;
        }

        GoodsDetailVO vo = result.getData();
        SpuInfo spuInfo = vo.getSpuInfo();
        List<GoodsDetailVO.SkuItem> skuList = vo.getSkuList();

        // 2. 数据转换 (Data Mapping)
        GoodsDoc doc = new GoodsDoc();
        // 2.1 基础信息拷贝
        doc.setId(spuInfo.getId());
        doc.setTitle(spuInfo.getSpuName()); // SPU名作为搜索标题
        doc.setSubTitle(spuInfo.getSpuDescription());
        doc.setBrandId(spuInfo.getBrandId());
        doc.setCategoryId(spuInfo.getCategoryId());
        doc.setCreateTime(spuInfo.getCreateTime());

        // 为了后续聚合方便，这里暂时存个空字符串，或者你可以在 GoodsDetailVO 里补充 BrandName/CategoryName
        // 这里的逻辑：如果有通过 Feign 拿到名字最好，如果没有，暂时置空，不影响核心搜索
        doc.setBrandName("");
        doc.setCategoryName("");

        // 2.2 计算价格 (取所有 SKU 中的最低价)
        // ✅ 修改为以下防御性代码 (Line 60-65 左右)
        if (skuList != null && !skuList.isEmpty()) {
            BigDecimal minPrice = skuList.stream()
                    .map(SkuInfo::getPrice)
                    .filter(price -> price != null) // 【核心修复】过滤掉 null 价格
                    .min(Comparator.naturalOrder())
                    .orElse(BigDecimal.ZERO);
            doc.setPrice(minPrice);

            // 设置图片等逻辑保持不变
            // 同时也建议加个判空，防止图片也是 null 导致后续报错
            String defaultImg = skuList.get(0).getSkuDefaultImg();
            doc.setImg(defaultImg != null ? defaultImg : "");

            long totalSale = skuList.stream()
                    .mapToLong(sku -> sku.getSaleCount() == null ? 0L : sku.getSaleCount()) // 防御性处理销量 null
                    .sum();
            doc.setSaleCount(totalSale);
        }

        // 3. 写入 ES
        goodsRepository.save(doc);
        log.info("商品上架同步成功：spuId={}, title={}", spuId, doc.getTitle());
        return true;
    }
}