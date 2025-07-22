package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true )
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class ExploringStoresApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExploringStoresApplication.class, args);
    }

}
