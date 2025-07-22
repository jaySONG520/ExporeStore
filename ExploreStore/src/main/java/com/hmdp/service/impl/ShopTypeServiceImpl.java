package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.hmdp.service.LocalCacheService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private LocalCacheService localCacheService;

    @Override
    public Result queryBylist() {
        String key = RedisConstants.CACHE_TYPE_KEY;
        // 1.查询缓存redis是否有所有数据
        List<ShopType> typeList = stringRedisTemplate.opsForList().range(key, 0, -1)
                .stream().map(type -> JSONUtil.toBean(type, ShopType.class)).collect(Collectors.toList());
        // 2.有则取出，返回给前端
        if (typeList != null && !typeList.isEmpty()) {
            return Result.ok(typeList);
        }
        // 3.无则访问数据库
        typeList = this.query().orderByAsc("sort").list();
        if (typeList.isEmpty()) {
            return Result.fail("没有商户类别");
        }
        List<String> listJson = typeList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        // 4.查询所有的数据，返回给redis
        stringRedisTemplate.opsForList().leftPushAll(key, listJson);
        stringRedisTemplate.expire(key, Duration.ofDays(1));
        // 5.返回给前端
        return Result.ok(typeList);

    }

    public ShopType getShopTypeByIdFromCache(Long id) {
        ShopType shopType = localCacheService.get("shopType:" + id, ShopType.class);
        if (shopType == null) {
            shopType = getById(id);
            if (shopType != null) {
                localCacheService.put("shopType:" + id, shopType);
            }
        }
        return shopType;
    }
}
