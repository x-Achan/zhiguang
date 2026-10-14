package com.zhiguang.counter.service.impl;

import com.zhiguang.counter.model.UserCounterType;
import com.zhiguang.counter.schema.UserCounterKeys;
import com.zhiguang.counter.service.UserCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * 用户维度计数实现。
 */
@Service
@RequiredArgsConstructor
public class UserCounterServiceImpl implements UserCounterService {

    private final StringRedisTemplate redis;

    /** 基础 TTL（秒） */
    private final long baseTtlSeconds = 7200;
    /** TTL 抖动上限（秒）：防雪崩 —— 一大批用户计数同时过期会集中回源 */
    private final long jitterSeconds = 1200;

    // 获取关注/粉丝数
    @Override
    public long getCount(long userId, UserCounterType type, LongSupplier truthSupplier) {
        String key = UserCounterKeys.user(userId, type);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        // Redis未命中，从数据库读真值，返回value
        long truth = truthSupplier.getAsLong();
        // 重建回 Redis
        writeThrough(userId, type, truth);
        return truth;
    }

    // 重建
    @Override
    public void writeThrough(long userId, UserCounterType type, long value) {
        String key = UserCounterKeys.user(userId, type);
        // TTL抖动防止雪崩
        long ttl = baseTtlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
        redis.opsForValue().set(key, String.valueOf(value), Duration.ofSeconds(ttl));
        // 登记索引，供对账抽样使用（避免 KEYS 全库扫描）
        redis.opsForSet().add(UserCounterKeys.INDEX, UserCounterKeys.indexEntry(userId, type));
    }
}