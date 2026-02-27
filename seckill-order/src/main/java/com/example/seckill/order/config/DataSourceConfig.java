package com.example.seckill.order.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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
        dataSource.setUrl("jdbc:mysql://localhost:3306/seckill_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai");
        dataSource.setUsername("root");
        dataSource.setPassword("Hzj760322");
        System.out.println(">>> 🚀 [强制配置] DataSource 已手动创建连接: " + dataSource.getUrl());
        return dataSource;
    }

    // 2. 手动创建 SqlSessionFactory (替代 MyBatis-Plus 自动配置)
    // 【修改点】添加 MybatisPlusInterceptor 参数，Spring 会自动注入我们在 MybatisPlusConfig 定义的那个 Bean
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor mybatisPlusInterceptor) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // 【关键修复】手动添加插件，否则分表拦截器不会生效！
        sessionFactory.setPlugins(mybatisPlusInterceptor);

        // 如果你是纯注解开发，这行可以注释；如果有 XML 需要解开
        // sessionFactory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/*.xml"));

        // 建议：如果你的项目依赖 yml 中的 mybatis-plus 配置（如驼峰映射），手动配置时可能会丢失
        // 可以在这里手动开启驼峰映射（虽然 MP 默认也是开启的）
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        sessionFactory.setConfiguration(configuration);

        return sessionFactory.getObject();
    }
}