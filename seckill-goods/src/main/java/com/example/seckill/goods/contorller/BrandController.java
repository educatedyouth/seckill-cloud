package com.example.seckill.goods.contorller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckill.common.result.Result;
import com.example.seckill.goods.entity.Brand;
import com.example.seckill.goods.service.BrandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/brand")
public class BrandController {

    @Autowired
    private BrandService brandService;

    /**
     * 模糊搜索接口 (Remote Search)
     * 避免一次性返回所有数据
     */
    @GetMapping("/search")
    public Result<List<Brand>> search(@RequestParam("keyword") String keyword) {
        LambdaQueryWrapper<Brand> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 模糊匹配名称
            query.like(Brand::getName, keyword);
        }
        // 限制返回条数，防止搜"a"炸出现几千条，取前20条即可
        query.last("LIMIT 20");
        return Result.success(brandService.list(query));
    }

    /**
     * 快速创建品牌 (Quick Create)
     * 当商户输入新品牌时调用
     */
    @PostMapping("/add")
    public Result<Brand> add(@RequestBody Brand brand) {
        // 1. 查重：防止 "Nike" 和 "Nike " 重复
        LambdaQueryWrapper<Brand> query = new LambdaQueryWrapper<>();
        query.eq(Brand::getName, brand.getName());
        Brand exist = brandService.getOne(query);

        if (exist != null) {
            return Result.success(exist); // 如果已存在，直接返回旧的
        }

        // 2. 初始化默认值
        brand.setShowStatus(1);
        brand.setSort(0);
        // 提取首字母 (这里简化处理，生产环境可以用 PinyinUtils)
        String firstLetter = brand.getName().substring(0, 1).toUpperCase();
        brand.setFirstLetter(firstLetter);

        brandService.save(brand);
        return Result.success(brand); // 返回带 ID 的新对象
    }

    // 保留之前的 list/all 兜底，但前端尽量少用
    @GetMapping("/list/all")
    public Result<List<Brand>> listAll() {
        return Result.success(brandService.list());
    }
    /**
     * 根据ID获取品牌详情 (用于前端回显)
     */
    @GetMapping("/get/{id}")
    public Result<Brand> getById(@PathVariable("id") Long id) {
        return Result.success(brandService.getById(id));
    }
}