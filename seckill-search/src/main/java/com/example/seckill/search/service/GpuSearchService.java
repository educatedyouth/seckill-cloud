package com.example.seckill.search.service;

import com.example.seckill.search.entity.GoodsDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;

/**
 * GPU 向量检索服务 (JNI 桥接层)
 */
@Service
public class GpuSearchService {

    // 1. 加载 C++ 编译生成的 DLL (名字要和后面生成的一致，这里暂定 seckill_gpu)
    static {
        try {
            System.load("D:\\JavaTest\\seckill-cloud\\seckill-search\\src\\main\\resources\\native-libs\\SeckillSearchGPU.dll");
            System.out.println(">>> [JNI] SeckillSearchGPU.dll 加载成功！");
        } catch (UnsatisfiedLinkError e) {
            System.err.println(">>> [JNI] 严重错误：未找到 SeckillSearchGPU.dll，请检查 java.library.path");
            e.printStackTrace();
        }
    }

    // ================== Native 接口定义 ==================

    /**
     * 初始化 GPU 数据
     * @param ids 商品ID数组
     * @param flatVectors 扁平化的向量数组 (大小 = rows * dim)
     * @param rows 商品数量
     * @param dim 向量维度 (固定 1024)
     */
    public native void loadGpuData(long[] ids, float[] flatVectors, int rows, int dim);

    /**
     * 执行 TopK 搜索
     * @param queryVector 查询向量 (1024维)
     * @param k 需要返回的前 K 个结果
     * @return TopK 的商品 ID 数组
     */
    public native long[] searchTopK(float[] queryVector, int k);

    /**
     * 释放 GPU 显存
     */
    public native void freeGpuMemory();

    // ================== 生命周期管理 ==================

    @PreDestroy
    public void destroy() {
        System.out.println(">>> [JNI]正在释放 GPU 资源...");
        freeGpuMemory();
    }

    @Autowired
    private com.example.seckill.search.repository.GoodsRepository goodsRepository;

    /**
     * 将全量商品加载到 GPU (启动时调用)
     */
    @PostConstruct
    public void initGpuData() {
        System.out.println(">>> [JNI] 开始全量加载数据到 GPU...");

        // 1. 从 DB/ES 拉取所有商品
        Iterable<com.example.seckill.search.entity.GoodsDoc> allDocs = goodsRepository.findAll();
        List<GoodsDoc> docList = new ArrayList<>();
        allDocs.forEach(docList::add);

        if (docList.isEmpty()) {
            System.out.println(">>> [JNI] 没有数据，跳过加载。");
            return;
        }

        int rows = docList.size();
        int dim = 1024; // 你的模型维度

        // 2. 扁平化处理 (Flattening)
        // 必须是一块连续的内存：[向量1, 向量2, ..., 向量N]
        long[] ids = new long[rows];
        float[] flatVectors = new float[rows * dim];

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < rows; i++) {
            var doc = docList.get(i);
            ids[i] = doc.getId();

            List<Float> vec = doc.getEmbeddingVector();
            if (vec != null && vec.size() == dim) {
                for (int j = 0; j < dim; j++) {
                    flatVectors[i * dim + j] = vec.get(j);
                }
            } else {
                System.out.println("没有向量，请务必详细检查！");
                // 如果没有向量，补0防止崩溃，或者跳过
                // 这里简单处理：补0
                for (int j = 0; j < dim; j++) flatVectors[i * dim + j] = 0.0f;
            }
        }
        long parseTime = System.currentTimeMillis();

        // 3. 调用 JNI 加载
        this.loadGpuData(ids, flatVectors, rows, dim);

        long endTime = System.currentTimeMillis();
        System.out.println(String.format(">>> [JNI] 数据加载完成。商品数: %d, 解析耗时: %dms, GPU拷贝耗时: %dms",
                rows, (parseTime - startTime), (endTime - parseTime)));
    }
}