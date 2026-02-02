package com.example.seckill.search.service;

import com.example.seckill.search.entity.GoodsDoc;
import com.example.seckill.search.repository.GoodsRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * GPU 向量检索服务 (JNI 桥接层)
 * 适配 C++ Version 6+ (FP16, Pinned Memory, Double Buffering)
 */
@Service
public class GpuSearchService {

    // ================== 配置常量 ==================
    // 最大容量：200万 (根据你的显存大小调整，FP16下 200万 * 1024 维约占 4GB 显存)
    private static final int MAX_CAPACITY = 1_000_000;
    // 向量维度：与 C++ 和 Embedding 模型保持一致
    private static final int DIM = 1024;
    // 模拟数据倍增系数：如果 DB 只有 10 条，乘以 100000 就是 100万条
    private static final int DATA_MULTIPLIER = 10000;

    // 定时任务调度器 (单线程即可)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Autowired
    private GoodsRepository goodsRepository;

    // 1. 加载 C++ 编译生成的 DLL
    static {
        try {
            String libPath = "D:\\JavaTest\\seckill-cloud\\seckill-search\\src\\main\\resources\\native-libs\\";
            // 加载 CUDA Runtime
            System.load(libPath + "cudart64_12.dll");
            System.out.println(">>> [JNI] cudart64 加载成功");

            // 加载 CUDA BLAS Light (必须在 cublas 之前或一起)
            System.load(libPath + "cublasLt64_12.dll");
            System.out.println(">>> [JNI] cublasLt64 加载成功");

            // 加载 CUDA BLAS (矩阵计算核心)
            System.load(libPath + "cublas64_12.dll");
            System.out.println(">>> [JNI] cublas64 加载成功");

            System.load(libPath + "ggml-base.dll");
            System.out.println(">>> [JNI] 依赖库 ggml-base.dll 加载成功！");

            System.load(libPath + "ggml-cuda.dll");
            System.out.println(">>> [JNI] 依赖库 ggml-cuda.dll 加载成功！");

            System.load(libPath + "ggml-cpu.dll");
            System.out.println(">>> [JNI] 依赖库 ggml-cuda.dll 加载成功！");

            System.load(libPath + "ggml.dll");
            System.out.println(">>> [JNI] 依赖库 ggml.dll 加载成功！");

            System.load(libPath + "llama.dll");
            System.out.println(">>> [JNI] 依赖库 llama.dll 加载成功！");

            // 2. 然后加载主 JNI 库
            System.load(libPath + "SeckillSearchGPU.dll");
            System.out.println(">>> [JNI] 主库 SeckillSearchGPU.dll 加载成功！");

        } catch (UnsatisfiedLinkError e) {
            System.err.println(">>> [JNI] 严重错误：DLL 加载失败！请检查路径和依赖。");
            System.err.println("错误详情: " + e.getMessage());
            e.printStackTrace();
            // 建议：如果加载失败，直接退出或抛出运行时异常，因为没有 GPU 服务无法运行
            throw new RuntimeException("GPU Search Native Library Load Failed", e);
        }
    }

    // ================== Native 接口定义 (对应 C++ extern "C") ==================

    /**
     * 1. 初始化双缓冲内存池 (启动时调用一次)
     * 对应 C++: Java_..._initDualBuffer
     */
    public native void initDualBuffer(int maxCapacity, int dim);

    /**
     * 2. 全量热更新 (启动时 + 定时调用)
     * 对应 C++: Java_..._hotUpdate
     * 将数据写入备用 Buffer 并原子切换
     */
    public native void hotUpdate(long[] ids, float[] flatVectors, int rows, int dim);

    /**
     * 3. 执行搜索
     * 对应 C++: Java_..._search
     */
    public native long[] search(String keyword, int k);

    /**
     * 4. 释放 GPU 显存
     * 对应 C++: Java_..._freeGpuMemory
     */
    public native void freeGpuMemory();

    // ================== 生命周期与业务逻辑 ==================

    // @PostConstruct
    public void init() {
        System.out.println(">>> [Service] 初始化 GPU 搜索服务...");

        // 1. 初始化 GPU 显存结构 (此时不加载数据)
        // 这一步会分配两块 MAX_CAPACITY 大小的显存
        this.initDualBuffer(MAX_CAPACITY, DIM);

        // 2. 立即执行一次全量数据加载
        refreshDataTask();

        // 3. 开启定时热更新任务 (例如：启动 5 分钟后开始，每 5 分钟执行一次)
        scheduler.scheduleAtFixedRate(this::refreshDataTask, 5, 10, TimeUnit.MINUTES);

        // 4. 预热
        warmUp();
    }

    @PreDestroy
    public void destroy() {
        System.out.println(">>> [Service] 正在停止服务...");
        scheduler.shutdown(); // 停止定时任务
        freeGpuMemory();      // 释放 C++ 资源
    }

    /**
     * 核心任务：从 DB 拉取数据 -> 构造大数组 -> 推送给 GPU
     */
    private void refreshDataTask() {
        try {
            long startTime = System.currentTimeMillis();
            System.out.println(">>> [Task] 开始全量数据同步...");

            // 1. 从 DB/ES 拉取所有基础商品
            Iterable<GoodsDoc> allDocs = goodsRepository.findAll();
            List<GoodsDoc> sourceList = new ArrayList<>();
            allDocs.forEach(sourceList::add);

            if (sourceList.isEmpty()) {
                System.out.println(">>> [Task] DB 无数据，跳过更新。");
                return;
            }

            // 2. 计算扩容后的总行数 (用于压测)
            // 实际生产环境不需要这个 Multiplier，直接用 sourceList.size() 即可
            int totalRows = sourceList.size() * DATA_MULTIPLIER;

            // 安全检查：防止溢出预设容量
            if (totalRows > MAX_CAPACITY) {
                System.err.println(">>> [Task] 警告：数据量 (" + totalRows + ") 超过 GPU 容量限制 (" + MAX_CAPACITY + ")，将进行截断。");
                totalRows = MAX_CAPACITY;
            }

            // 3. 准备大数组 (Java Heap -> Pinned Memory 的源头)
            long[] ids = new long[totalRows];
            float[] flatVectors = new float[totalRows * DIM];

            // 4. 扁平化处理 (Flattening) + 数据倍增
            // 逻辑：循环遍历源列表，生成海量数据填满数组
            for (int i = 0; i < totalRows; i++) {
                // 取模循环：0, 1, 2 ... N, 0, 1 ...
                GoodsDoc doc = sourceList.get(i % sourceList.size());

                // 生成唯一 ID (为了压测区分，我们用 i 作为 ID，或者用 doc.getId() + 偏移量)
                // 生产环境直接用: ids[i] = doc.getId();
                ids[i] = doc.getId();

                List<Float> vec = doc.getEmbeddingVector();
                if (vec != null && vec.size() == DIM) {
                    for (int j = 0; j < DIM; j++) {
                        flatVectors[i * DIM + j] = vec.get(j);
                    }
                } else {
                    // 异常向量补 0，防止 C++ 越界
                    for (int j = 0; j < DIM; j++) flatVectors[i * DIM + j] = 0.001f;
                }
            }

            long parseTime = System.currentTimeMillis();
            System.out.println(String.format(">>> [Task] 数据准备完成 (Java端). 耗时: %dms. 开始推送到 GPU...", (parseTime - startTime)));

            // 5. 🚀 调用 JNI 热更新接口
            // 这个过程会将数据拷贝到 GPU 的 Standby Buffer，然后原子切换
            this.hotUpdate(ids, flatVectors, totalRows, DIM);

            long endTime = System.currentTimeMillis();
            System.out.println(String.format(">>> [Task] 热更新成功！当前 GPU 商品数: %d, 总耗时: %dms", totalRows, (endTime - startTime)));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(">>> [Task] 热更新失败: " + e.getMessage());
        }
    }

    private void warmUp() {
        try {
            System.out.println(">>> [Warmup] 正在预热 Ollama 模型和 GPU 计算上下文...");
            // 发送一个无意义的请求，强制触发 C++ -> Ollama -> CUDA 流程
            // 确保 C++ 的 Stream 和 Cublas Handle 完成懒加载
            this.search("warmup_initialization", 1);
            System.out.println(">>> [Warmup] 预热完成。");
        } catch (Exception e) {
            System.err.println(">>> [Warmup] 预热失败 (非致命): " + e.getMessage());
        }
    }
}