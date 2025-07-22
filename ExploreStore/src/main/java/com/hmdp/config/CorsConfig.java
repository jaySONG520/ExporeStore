package com.hmdp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:8080") // 或你前端地址
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true) // ✅ 必须启用，允许带 Cookie
                .maxAge(3600);
    }
}
