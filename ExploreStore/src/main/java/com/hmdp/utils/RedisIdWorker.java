package com.hmdp.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //keyPrefix是value（业务前缀）
    private static final long BEGIN_TIMESTAMP = 1752844860L;
    private static final int COUNT_BITS=32;
    public Long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowsecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowsecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1获取当前日期，精切到天
        String date =now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd "));
        //value就是从1开始的
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //3.拼接并返回
        return timestamp <<COUNT_BITS|count;
    }



    public static void main(String[] args){
        LocalDateTime time = LocalDateTime.of(2025, 7, 18, 13, 21, 0);
        //变成时间戳
        long second = time.toEpochSecond(ZoneOffset.UTC);
    }
}


































































