// seckill-goods/src/main/java/com/example/seckill/goods/service/impl/GoodsServiceImpl.java
package com.example.seckill.goods.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.common.entity.SkuImages;
import com.example.seckill.common.entity.SkuInfo;
import com.example.seckill.common.entity.SkuSaleAttrValue;
import com.example.seckill.common.entity.SpuInfo;
import com.example.seckill.goods.dto.SkuSaleAttrDTO;
import com.example.seckill.goods.dto.SkuSaveDTO;
import com.example.seckill.goods.dto.SpuSaveDTO;
import com.example.seckill.goods.mapper.*;
import com.example.seckill.goods.service.GoodsService;
import com.example.seckill.common.vo.GoodsDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.stream.Collectors;
@Slf4j // 【新增】添加日志注解
@Service
public class GoodsServiceImpl extends ServiceImpl<SpuInfoMapper, SpuInfo> implements GoodsService {

    @Autowired
    private SkuInfoMapper skuInfoMapper;
    @Autowired
    private SkuImagesMapper skuImagesMapper;
    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    // 【新增】注入 RocketMQ 模板
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    // 【新增】读取 Topic 配置
    @Value("${app.rocketmq.goods-up-topic}")
    private String goodsUpTopic;

    @Value("${app.rocketmq.goods-down-topic}")
    private String goodsDownTopic;

    @Override
    public void deleteOneBySpuID(Long spuID){
        String spuIdStr = String.valueOf(spuID);
        rocketMQTemplate.syncSend(goodsDownTopic, MessageBuilder.withPayload(spuIdStr).build());
    }

    @Transactional(rollbackFor = Exception.class) // 【关键】开启事务，任何一步失败全部回滚
    @Override
    public void saveGoods(SpuSaveDTO dto) {
        // 1. 保存 SPU 基本信息 (pms_spu_info)
        SpuInfo spuInfo = new SpuInfo();
        BeanUtils.copyProperties(dto, spuInfo);
        spuInfo.setCreateTime(new Date());
        spuInfo.setUpdateTime(new Date());
        spuInfo.setUserId(dto.getUserId()); // 【新增】保存归属人
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
                skuInfo.setSkuTitle(spuInfo.getSpuName() + " 规格：" + skuInfo.getSkuName());
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
                    }).toList();

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
                    }).toList();

                    for (SkuSaleAttrValue val : attrValues) {
                        skuSaleAttrValueMapper.insert(val);
                    }
                }
            }
        }
        // 【核心修正】事务提交成功后，再发送 MQ 消息
        // 防止“数据库回滚但消息已发出”导致的 ES 数据不一致
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 1. 转为 String 发送
                String spuIdStr = String.valueOf(spuId);
                rocketMQTemplate.syncSend(goodsUpTopic, MessageBuilder.withPayload(spuIdStr).build());
                log.info(">>> [事务提交后] 商品上架消息发送成功，spuId: {}", spuId);
            }
        });
    }
    @Override
    public GoodsDetailVO getGoodsDetail(Long spuId) {
        // 1. 查询 SPU 主表
        SpuInfo spuInfo = this.getById(spuId);
        if (spuInfo == null) {
            return null;
        }

        // 2. 查询该 SPU 下所有的 SKU
        LambdaQueryWrapper<SkuInfo> skuQuery = new LambdaQueryWrapper<>();
        skuQuery.eq(SkuInfo::getSpuId, spuId);
        List<SkuInfo> rawSkuList = skuInfoMapper.selectList(skuQuery);

        if (rawSkuList == null || rawSkuList.isEmpty()) {
            GoodsDetailVO vo = new GoodsDetailVO();
            vo.setSpuInfo(spuInfo);
            return vo;
        }

        // 3. 收集所有的 skuId (这是批量查询的基础，防止循环查库)
        List<Long> skuIds = rawSkuList.stream()
                .map(SkuInfo::getSkuId)
                .collect(Collectors.toList());

        // 4.1 批量查询所有相关 SKU 的销售属性 (pms_sku_sale_attr_value)
        LambdaQueryWrapper<SkuSaleAttrValue> attrQuery = new LambdaQueryWrapper<>();
        attrQuery.in(SkuSaleAttrValue::getSkuId, skuIds);
        attrQuery.orderByAsc(SkuSaleAttrValue::getAttrSort);
        List<SkuSaleAttrValue> allAttrValues = skuSaleAttrValueMapper.selectList(attrQuery);

        // 4.2 【新增】批量查询所有相关 SKU 的图片 (pms_sku_images)
        // 架构师批注：这是为了解决前端无法切换图片的问题。必须一次性查出，禁止在循环中调用 mapper
        LambdaQueryWrapper<SkuImages> imgQuery = new LambdaQueryWrapper<>();
        imgQuery.in(SkuImages::getSkuId, skuIds);
        List<SkuImages> allImages = skuImagesMapper.selectList(imgQuery);

        // 5. 内存聚合 (Memory Aggregation)
        // 5.1 分组属性
        Map<Long, List<SkuSaleAttrValue>> attrGroupMap = allAttrValues.stream()
                .collect(Collectors.groupingBy(SkuSaleAttrValue::getSkuId));

        // 5.2 【新增】分组图片
        Map<Long, List<SkuImages>> imgGroupMap = allImages.stream()
                .collect(Collectors.groupingBy(SkuImages::getSkuId));

        // 6. 组装最终的 SKUItem 列表
        List<GoodsDetailVO.SkuItem> skuItems = rawSkuList.stream().map(sku -> {
            GoodsDetailVO.SkuItem item = new GoodsDetailVO.SkuItem();
            BeanUtils.copyProperties(sku, item);

            // 设置对应的属性列表
            item.setSaleAttrValues(attrGroupMap.get(sku.getSkuId()));

            // 【新增】设置对应的图片列表
            item.setImages(imgGroupMap.get(sku.getSkuId()));

            return item;
        }).collect(Collectors.toList());

        // 7. 封装最终 VO
        GoodsDetailVO vo = new GoodsDetailVO();
        vo.setSpuInfo(spuInfo);
        vo.setSkuList(skuItems);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGoods(SpuSaveDTO dto) {
        // 1. 更新 SPU 主表
        SpuInfo spuInfo = new SpuInfo();
        BeanUtils.copyProperties(dto, spuInfo);
        spuInfo.setId(dto.getId()); // 确保 DTO 里有 id
        spuInfo.setUpdateTime(new Date());
        this.baseMapper.updateById(spuInfo);

        // 2. 处理 SKU 列表 (核心难点：增删改的智能判断)
        Long spuId = dto.getId();
        List<SkuSaveDTO> dtoSkus = dto.getSkus(); // 前端传来的新列表

        // 2.1 查出数据库中旧的 SKU 列表
        LambdaQueryWrapper<SkuInfo> query = new LambdaQueryWrapper<>();
        query.eq(SkuInfo::getSpuId, spuId);
        List<SkuInfo> dbSkus = skuInfoMapper.selectList(query);

        // 构造 DB SKU 的 Map，方便查找 (Key: skuId)
        Map<Long, SkuInfo> dbSkuMap = dbSkus.stream().collect(Collectors.toMap(SkuInfo::getSkuId, s -> s));

        // 2.2 遍历前端传来的列表，区分是“更新”还是“新增”
        // 收集需要保留/更新的 skuId
        Set<Long> keepSkuIds = new HashSet<>();

        if (dtoSkus != null && !dtoSkus.isEmpty()) {
            for (SkuSaveDTO skuDTO : dtoSkus) {
                if (skuDTO.getSkuId() != null && dbSkuMap.containsKey(skuDTO.getSkuId())) {
                    // === A. 更新逻辑 (保留 ID) ===
                    keepSkuIds.add(skuDTO.getSkuId());
                    SkuInfo updateSku = new SkuInfo();
                    BeanUtils.copyProperties(skuDTO, updateSku);
                    // 强制覆盖关键外键，防止前端乱传
                    updateSku.setSpuId(spuId);
                    // 这里不重置 saleCount
                    skuInfoMapper.updateById(updateSku);

                    // 更新附属表：图片和销售属性
                    // 策略：附属表数据量小且依赖 ID，直接【全删全插】最稳妥，避免复杂的 Diff
                    updateSkuImagesAndAttrs(skuDTO.getSkuId(), skuDTO);

                } else {
                    // === B. 新增逻辑 ===
                    SkuInfo newSku = new SkuInfo();
                    BeanUtils.copyProperties(skuDTO, newSku);
                    newSku.setSpuId(spuId);
                    newSku.setSaleCount(0L);

                    // 处理默认图
                    String defaultImg = skuDTO.getDefaultImg();
                    if (defaultImg == null && skuDTO.getImages() != null && !skuDTO.getImages().isEmpty()) {
                        defaultImg = skuDTO.getImages().get(0);
                    }
                    newSku.setSkuDefaultImg(defaultImg);

                    skuInfoMapper.insert(newSku);

                    // 插入附属表
                    updateSkuImagesAndAttrs(newSku.getSkuId(), skuDTO);
                }
            }
        }

        // 2.3 处理删除逻辑
        // 数据库里有，但 keepSkuIds 里没有的，就是被用户删掉的 SKU
        List<Long> idsToDelete = dbSkuMap.keySet().stream()
                .filter(id -> !keepSkuIds.contains(id))
                .collect(Collectors.toList());

        if (!idsToDelete.isEmpty()) {
            // 批量物理删除 SKU (也可以做逻辑删除，视业务要求，这里演示物理删除)
            skuInfoMapper.deleteBatchIds(idsToDelete);

            // 级联删除附属表 (图片、属性)
            LambdaQueryWrapper<SkuImages> imgDel = new LambdaQueryWrapper<>();
            imgDel.in(SkuImages::getSkuId, idsToDelete);
            skuImagesMapper.delete(imgDel);

            LambdaQueryWrapper<SkuSaleAttrValue> attrDel = new LambdaQueryWrapper<>();
            attrDel.in(SkuSaleAttrValue::getSkuId, idsToDelete);
            skuSaleAttrValueMapper.delete(attrDel);
        }
        // 3. 【核心补充】发送同步消息 (修改 = 重新上架/覆盖)
        // 只有当数据库事务完全提交后，才通知 ES 更新，防止查到旧数据
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    String spuIdStr = String.valueOf(spuId);
                    // 发送到 "上架" Topic，ES 消费者收到后会重新拉取最新数据并覆盖写入
                    rocketMQTemplate.syncSend(goodsUpTopic, MessageBuilder.withPayload(spuIdStr).build());
                    log.info(">>> [事务提交后] 商品修改同步消息发送成功，spuId: {}", spuId);
                } catch (Exception e) {
                    log.error(">>> 商品修改同步消息发送失败，spuId: {}", spuId, e);
                    // 注意：这里是在事务提交后执行的，即使发消息失败，数据库修改也已经生效了。
                    // 生产环境通常会配合本地消息表或重试机制，这里暂时打印错误日志。
                }
            }
        });
    }

    // 辅助方法：更新 SKU 的附属信息 (先删后插)
    private void updateSkuImagesAndAttrs(Long skuId, SkuSaveDTO skuDTO) {
        // 1. 清理旧图
        LambdaQueryWrapper<SkuImages> imgDel = new LambdaQueryWrapper<>();
        imgDel.eq(SkuImages::getSkuId, skuId);
        skuImagesMapper.delete(imgDel);

        // 2. 插入新图
        if (skuDTO.getImages() != null && !skuDTO.getImages().isEmpty()) {
            for (String url : skuDTO.getImages()) {
                SkuImages img = new SkuImages();
                img.setSkuId(skuId);
                img.setImgUrl(url);
                // 简单判断是否默认图
                img.setDefaultImg(url.equals(skuDTO.getDefaultImg()) ? 1 : 0);
                skuImagesMapper.insert(img);
            }
        }

        // 3. 清理旧属性
        LambdaQueryWrapper<SkuSaleAttrValue> attrDel = new LambdaQueryWrapper<>();
        attrDel.eq(SkuSaleAttrValue::getSkuId, skuId);
        skuSaleAttrValueMapper.delete(attrDel);

        // 4. 插入新属性
        if (skuDTO.getSaleAttrs() != null) {
            for (SkuSaleAttrDTO attr : skuDTO.getSaleAttrs()) {
                SkuSaleAttrValue val = new SkuSaleAttrValue();
                BeanUtils.copyProperties(attr, val);
                val.setSkuId(skuId);
                skuSaleAttrValueMapper.insert(val);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long spuId, Integer status) {
        // 1. 修改数据库状态
        SpuInfo spuInfo = new SpuInfo();
        spuInfo.setId(spuId);
        spuInfo.setPublishStatus(status);
        spuInfo.setUpdateTime(new Date());
        this.baseMapper.updateById(spuInfo);

        // 2. 发送 MQ 消息 (根据状态决定发上架还是下架)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                String spuIdStr = String.valueOf(spuId);

                if (status == 1) {
                    // === 上架操作 ===
                    rocketMQTemplate.syncSend(goodsUpTopic, MessageBuilder.withPayload(spuIdStr).build());
                    log.info(">>> [事务提交后] 商品状态更新-上架消息发送成功，spuId: {}", spuId);
                } else {
                    // === 下架操作 (0 或其他非1状态) ===
                    rocketMQTemplate.syncSend(goodsDownTopic, MessageBuilder.withPayload(spuIdStr).build());
                    log.info(">>> [事务提交后] 商品状态更新-下架消息发送成功，spuId: {}", spuId);
                }
            }
        });
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeGoods(Long spuId) {
        // 1. 查出该 SPU 下所有的 SKU信息，我们需要用到 skuId
        LambdaQueryWrapper<SkuInfo> skuQuery = new LambdaQueryWrapper<>();
        skuQuery.eq(SkuInfo::getSpuId, spuId);
        List<SkuInfo> skuList = skuInfoMapper.selectList(skuQuery);

        // 如果有 SKU，需要先清理 SKU 相关联的从表数据
        if (skuList != null && !skuList.isEmpty()) {
            List<Long> skuIds = skuList.stream()
                    .map(SkuInfo::getSkuId)
                    .collect(Collectors.toList());

            // 2. 批量删除 SKU 图片 (pms_sku_images)
            LambdaQueryWrapper<SkuImages> imgWrapper = new LambdaQueryWrapper<>();
            imgWrapper.in(SkuImages::getSkuId, skuIds);
            skuImagesMapper.delete(imgWrapper);

            // 3. 批量删除 SKU 销售属性 (pms_sku_sale_attr_value)
            LambdaQueryWrapper<SkuSaleAttrValue> attrWrapper = new LambdaQueryWrapper<>();
            attrWrapper.in(SkuSaleAttrValue::getSkuId, skuIds);
            skuSaleAttrValueMapper.delete(attrWrapper);

            // 4. 批量删除 SKU 本身 (pms_sku_info)
            skuInfoMapper.deleteBatchIds(skuIds);
        }

        // 5. 最后删除 SPU 主表信息 (pms_spu_info)
        this.removeById(spuId);
        // 【核心修正】事务提交成功后，再发送 MQ 消息
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 1. 转为 String 发送
                String spuIdStr = String.valueOf(spuId);
                rocketMQTemplate.syncSend(goodsDownTopic, MessageBuilder.withPayload(spuIdStr).build());
                log.info(">>> [事务提交后] 商品下架消息发送成功，spuId: {}", spuId);
            }
        });
    }
    @Override
    public void syncAllGoods() {
        // 1. 查询所有商品的 ID (只查主键，性能极高)
        // selectObjs 默认返回第一列(id)
        List<Object> idList = this.baseMapper.selectObjs(
                new LambdaQueryWrapper<SpuInfo>().select(SpuInfo::getId)
        );

        if (idList == null || idList.isEmpty()) {
            log.warn(">>> 全量同步中止：数据库中没有商品");
            return;
        }

        log.info(">>> 开始执行全量同步，共扫描到 {} 个商品", idList.size());

        // 2. 循环发送 MQ 消息
        // 建议：如果数据量达到十万级，可以考虑使用线程池异步发送，或者批量发送
        // 但对于目前的量级，循环同步发送最稳健
        int successCount = 0;
        for (Object idObj : idList) {
            Long spuId = (Long) idObj;
            try {
                // 复用之前的上架逻辑：转为 String 发送
                String spuIdStr = String.valueOf(spuId);
                rocketMQTemplate.syncSend(goodsUpTopic, MessageBuilder.withPayload(spuIdStr).build());
                successCount++;
            } catch (Exception e) {
                log.error(">>> 全量同步-发送消息失败，spuId: {}", spuId, e);
            }
        }

        log.info(">>> 全量同步消息发送完毕，成功发送: {}/{}", successCount, idList.size());
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reduceStockDB(Long skuId, Integer count) {
        // 直接执行 Update 语句，利用数据库返回值判断是否扣减成功
        int rows = skuInfoMapper.reduceStock(skuId, count);
        if (rows > 0) {
            return true;
        } else {
            // 库存不足，抛出异常触发布式事务回滚
            throw new RuntimeException("商品库存不足，SKU:" + skuId);
        }
    }
}