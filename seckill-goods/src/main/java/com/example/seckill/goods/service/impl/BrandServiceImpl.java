package com.example.seckill.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.goods.entity.Brand;
import com.example.seckill.goods.mapper.BrandMapper;
import com.example.seckill.goods.service.BrandService;
import org.springframework.stereotype.Service;

@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements BrandService {
}