package com.example.seckill.order.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * 暴力解决数据库连接问题：手动配置 DataSource
 */
@Configuration
public class DataSourceConfig {

    // 1. 手动创建数据源 (替代 YAML 里的 spring.datasource 配置)
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // 注意：这里硬编码你的配置，确保绝对正确
        dataSource.setUrl("jdbc:mysql://100.113.176.73:3306/seckill_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        dataSource.setUsername("root");
        dataSource.setPassword("root");
        System.out.println(">>> 🚀 [强制配置] DataSource 已手动创建连接: " + dataSource.getUrl());
        return dataSource;
    }

    // 2. 手动创建 SqlSessionFactory (替代 MyBatis-Plus 自动配置)
    // 这一步是为了解决 'sqlSessionFactory required' 报错的终极杀招
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // 如果你有 mapper.xml 文件，需要在这里指定路径；如果是纯注解，这行可以忽略
        // sessionFactory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/*.xml"));

        return sessionFactory.getObject();
    }
}