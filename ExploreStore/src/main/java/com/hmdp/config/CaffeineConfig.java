package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {
    /**
     * Caffeine本地缓存配置：
     * - expireAfterWrite: 写入后10分钟自动过期
     * - maximumSize: 最大缓存10000条，超出后自动淘汰最久未使用的数据
     */
    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10000);
    }
}