package com.hmdp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hmdp.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Slf4j
@Service
public class CanalSyncService {

    @Autowired
    private LocalCacheService localCacheService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Autowired
    private IShopService shopService;

    @Autowired
    private IBlogService blogService;

    @Autowired
    private IShopTypeService shopTypeService;

    @Autowired
    private IVoucherService voucherService;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        // 注册模块以正确处理Java 8时间类型（如LocalDateTime）的序列化和反序列化
        objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 同步 User 缓存
     * 
     * @param userId 用户ID
     */
    public void syncUserById(Long userId) {
        // 1. 从数据库查询最新数据
        User user = userService.getById(userId);
        String localKey = "user:" + userId;
        String redisKey = "redis:user:" + userId;

        if (user == null) {
            // 数据库已删除，同步删除缓存
            log.info("删除User缓存, localKey: {}, redisKey: {}", localKey, redisKey);
            localCacheService.remove(localKey);
            stringRedisTemplate.delete(redisKey);
            return;
        }

        // 2. 同步更新本地缓存
        log.info("更新User本地缓存, key: {}", localKey);
        localCacheService.put(localKey, user);

        // 3. 同步更新Redis缓存
        try {
            log.info("更新User Redis缓存, key: {}", redisKey);
            String json = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set(redisKey, json);
        } catch (JsonProcessingException e) {
            log.error("序列化User对象失败, userId: " + userId, e);
        }
    }

    /**
     * 同步 Shop 缓存
     * 
     * @param shopId 商铺ID
     */
    public void syncShopById(Long shopId) {
        Shop shop = shopService.getById(shopId);
        String localKey = "shop:" + shopId;
        String redisKey = "redis:shop:" + shopId;

        if (shop == null) {
            log.info("删除Shop缓存, localKey: {}, redisKey: {}", localKey, redisKey);
            localCacheService.remove(localKey);
            stringRedisTemplate.delete(redisKey);
            return;
        }

        log.info("更新Shop本地缓存, key: {}", localKey);
        localCacheService.put(localKey, shop);

        try {
            log.info("更新Shop Redis缓存, key: {}", redisKey);
            String json = objectMapper.writeValueAsString(shop);
            stringRedisTemplate.opsForValue().set(redisKey, json);
        } catch (JsonProcessingException e) {
            log.error("序列化Shop对象失败, shopId: " + shopId, e);
        }
    }

    /**
     * 同步 Blog 缓存
     * 
     * @param blogId 博客ID
     */
    public void syncBlogById(Long blogId) {
        Blog blog = blogService.getById(blogId);
        String localKey = "blog:" + blogId;
        String redisKey = "redis:blog:" + blogId;

        if (blog == null) {
            log.info("删除Blog缓存, localKey: {}, redisKey: {}", localKey, redisKey);
            localCacheService.remove(localKey);
            stringRedisTemplate.delete(redisKey);
            return;
        }

        log.info("更新Blog本地缓存, key: {}", localKey);
        localCacheService.put(localKey, blog);

        try {
            log.info("更新Blog Redis缓存, key: {}", redisKey);
            String json = objectMapper.writeValueAsString(blog);
            stringRedisTemplate.opsForValue().set(redisKey, json);
        } catch (JsonProcessingException e) {
            log.error("序列化Blog对象失败, blogId: " + blogId, e);
        }
    }

    /**
     * 同步 ShopType 缓存
     * 
     * @param typeId 类型ID
     */
    public void syncShopTypeById(Long typeId) {
        ShopType shopType = shopTypeService.getById(typeId);
        String localKey = "shopType:" + typeId;
        String redisKey = "redis:shopType:" + typeId;

        if (shopType == null) {
            log.info("删除ShopType缓存, localKey: {}, redisKey: {}", localKey, redisKey);
            localCacheService.remove(localKey);
            stringRedisTemplate.delete(redisKey);
            return;
        }

        log.info("更新ShopType本地缓存, key: {}", localKey);
        localCacheService.put(localKey, shopType);

        try {
            log.info("更新ShopType Redis缓存, key: {}", redisKey);
            String json = objectMapper.writeValueAsString(shopType);
            stringRedisTemplate.opsForValue().set(redisKey, json);
        } catch (JsonProcessingException e) {
            log.error("序列化ShopType对象失败, typeId: " + typeId, e);
        }
    }

    /**
     * 同步 Voucher 缓存
     * 
     * @param voucherId 优惠券ID
     */
    public void syncVoucherById(Long voucherId) {
        Voucher voucher = voucherService.getById(voucherId);
        String localKey = "voucher:" + voucherId;
        String redisKey = "redis:voucher:" + voucherId;

        if (voucher == null) {
            log.info("删除Voucher缓存, localKey: {}, redisKey: {}", localKey, redisKey);
            localCacheService.remove(localKey);
            stringRedisTemplate.delete(redisKey);
            return;
        }

        log.info("更新Voucher本地缓存, key: {}", localKey);
        localCacheService.put(localKey, voucher);

        try {
            log.info("更新Voucher Redis缓存, key: {}", redisKey);
            String json = objectMapper.writeValueAsString(voucher);
            stringRedisTemplate.opsForValue().set(redisKey, json);
        } catch (JsonProcessingException e) {
            log.error("序列化Voucher对象失败, voucherId: " + voucherId, e);
        }
    }
}