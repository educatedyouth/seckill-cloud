package com.example.seckill.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.goods.entity.Category;
import com.example.seckill.goods.mapper.CategoryMapper;
import com.example.seckill.goods.service.CategoryService;
import com.example.seckill.goods.vo.CategoryTreeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

// seckill-goods/src/main/java/com/example/seckill/goods/service/impl/CategoryServiceImpl.java
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<CategoryTreeVO> listWithTree() {
        // 1. 查出所有分类 (通常分类表数据量不大，一次全查在内存组装性能更好)
        List<Category> entities = baseMapper.selectList(null);

        // 2. Entity -> VO 转换
        List<CategoryTreeVO> vos = entities.stream().map(e -> {
            CategoryTreeVO vo = new CategoryTreeVO();
            BeanUtils.copyProperties(e, vo);
            return vo;
        }).collect(Collectors.toList());

        // 3. 组装父子树形结构
        // 找到所有一级分类 (parentId == 0)
        List<CategoryTreeVO> level1Menus = vos.stream()
                .filter(category -> category.getParentId() == 0)
                .map(menu -> {
                    menu.setChildren(getChildrens(menu, vos));
                    return menu;
                })
                .sorted((menu1, menu2) -> {
                    return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
                })
                .collect(Collectors.toList());

        return level1Menus;
    }

    // 递归查找子菜单
    private List<CategoryTreeVO> getChildrens(CategoryTreeVO root, List<CategoryTreeVO> all) {
        List<CategoryTreeVO> children = all.stream()
                .filter(category -> category.getParentId().equals(root.getId()))
                .map(category -> {
                    // 递归点：继续找子菜单的子菜单
                    category.setChildren(getChildrens(category, all));
                    return category;
                })
                .sorted((menu1, menu2) -> {
                    return (menu1.getSort() == null ? 0 : menu1.getSort()) - (menu2.getSort() == null ? 0 : menu2.getSort());
                })
                .collect(Collectors.toList());
        return children;
    }
}