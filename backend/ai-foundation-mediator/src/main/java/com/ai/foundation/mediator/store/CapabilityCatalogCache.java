package com.ai.foundation.mediator.store;

import com.ai.foundation.com.constant.RedisKeyConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityCatalogCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final long TTL_MINUTES = 30;

    public void invalidate(String productCode) {
        String key = RedisKeyConstants.CAPABILITY_CATALOG + ":" + productCode;
        Boolean deleted = stringRedisTemplate.delete(key);
        log.info("失效能力目录缓存 productCode={} deleted={}", productCode, deleted);
    }

    public void invalidateAll() {
        var keys = stringRedisTemplate.keys(RedisKeyConstants.CAPABILITY_CATALOG + ":*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        log.info("失效全部能力目录缓存 count={}", keys == null ? 0 : keys.size());
    }

    public void put(String productCode, Object catalog) {
        try {
            String key = RedisKeyConstants.CAPABILITY_CATALOG + ":" + productCode;
            String value = objectMapper.writeValueAsString(catalog);
            stringRedisTemplate.opsForValue().set(key, value, TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入能力目录缓存失败 productCode={}", productCode, e);
        }
    }
}
