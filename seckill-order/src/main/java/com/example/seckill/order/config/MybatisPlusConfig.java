package com.example.seckill.order.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.example.seckill.order.context.TableContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 动态表名插件（MyBatis-Plus 3.5.x 正确用法）
        DynamicTableNameInnerInterceptor dynamicTableInterceptor =
                new DynamicTableNameInnerInterceptor();

        dynamicTableInterceptor.setTableNameHandler((sql, tableName) -> {
            // 只处理 order_tbl，其它表直接放行
            if (!"order_tbl".equals(tableName)) {
                return tableName;
            }

            String dynamicTableName = TableContext.get();

            // fail-fast：上下文为空时仍返回原表名，便于尽早暴露问题
            return dynamicTableName != null ? dynamicTableName : tableName;
        });

        interceptor.addInnerInterceptor(dynamicTableInterceptor);
        return interceptor;
    }
}
