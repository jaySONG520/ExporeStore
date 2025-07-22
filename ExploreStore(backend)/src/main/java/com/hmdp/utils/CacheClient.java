package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //传入任意对象
    public void set(String key, Object value, Long time , TimeUnit Unit ) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, Unit);
    }
    //传入逻辑过期
    public void setWithLogicalExpire(String key, Object value, Long time , TimeUnit Unit ) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(Unit.toSeconds(time)));
        //写入redis
        // 自定义时间格式
        JSONConfig config = new JSONConfig();
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");
        //写入redis
        String jsonStr = JSONUtil.toJsonStr(redisData, config);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


        //缓存穿透
        public <R,ID>R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                            Function<ID,R>dbFallback,Long time, TimeUnit unit) {
            String key=keyPrefix+id;
            //1.查询redis缓存
            String Json = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isNotBlank(Json)){
                //2。存在返回

                return  JSONUtil.toBean(Json, type);
            }
            //判断命中是否是空值(空值不等于空字符串)
            if (Json !=null) {
                return null;
            }
            //3.不存在，查询数据库
            R r = dbFallback.apply(id);
            //4.数据库中没有数据，返回警告
            if(r==null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //5.数据库数据返回给redis，以便下次使用
           this.set(key,r,time,unit);
            //6.数据库存在数据，返回给前端
            return r;
        }
    /**
     * 设置逻辑过期（缓存击穿）
     * @param id
     * @return
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID>R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,
                                          Function<ID,R>dbFallback,Long time,TimeUnit unit) {
        String key=keyPrefix+id;
        //1.查询redis缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(Json)){
            return null;
        }
        //4.命中，需要把json序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expiretime = redisData.getExpireTime();
        //5.判断是否过期
        if(expiretime.isAfter(LocalDateTime.now())){
            //5.1未过期，返回信息
            return r;
        }

        //5.2已过期，缓存重建
        //6.缓存重建
        //6.1获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //6.2判断是否锁成功
        if(isLock){
            //6.3成功，另开一个线成进行重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    R r1  =dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }

            });
        }

        //6.4返回过期信息
        return r;
    }


    private boolean tryLock(String key){
        boolean flag=  stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}





















