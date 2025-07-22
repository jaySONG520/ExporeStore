package com.hmdp.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocalCacheService {
    private final Cache<String, Object> cache;

    @Autowired
    public LocalCacheService(Caffeine<Object, Object> caffeine) {
        this.cache = caffeine.build();
    }

    public <T> void put(String key, T value) {
        cache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = cache.getIfPresent(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    public void remove(String key) {
        cache.invalidate(key);
    }
}