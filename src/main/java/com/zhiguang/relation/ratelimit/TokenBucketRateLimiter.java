package com.zhiguang.relation.ratelimit;

import com.zhiguang.relation.schema.RelationKeys;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 令牌桶限流（Lua 实现，原子）。
 *
 * <p>桶结构：Hash，两个字段 {@code last}（上次补充时间，秒）、{@code tokens}（当前令牌数）。
 * 读请求时先按经过的时间补充令牌（不超过容量），再尝试扣 1 个。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenBucketRateLimiter {

    /**
     * <b>★ 时间必须取 Redis 的 TIME，不能由 Java 传进来。</b>
     *
     * <p>限流状态是<b>多个应用实例共享</b>的。如果每个实例用自己的系统时钟算"过了多久"，
     * 时钟快慢不一的服务器会按各自的节奏补充令牌，限流就形同虚设。
     * 用 Redis 的时间，所有实例共用一个时间基准。</p>
     *
     * <p>注：{@code TIME} 是非确定性命令。Redis 5 之前需要在脚本里显式
     * {@code redis.replicate_commands()}，否则主从复制会因为脚本重放而分叉；
     * <b>Redis 5+ 默认改成"效果复制"</b>（只复制脚本产生的写命令），所以现在是安全的。</p>
     *
     * <p>KEYS[1]=桶键　ARGV[1]=容量　ARGV[2]=每秒补充速率<br>
     * 返回 1=取到令牌；0=桶空被拒</p>
     */
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])

            local now = redis.call('TIME')[1]
            local last = redis.call('HGET', key, 'last')
            local tokens = redis.call('HGET', key, 'tokens')
            if not last then
                last = now
                tokens = capacity
            end

            local elapsed = tonumber(now) - tonumber(last)
            tokens = math.min(capacity, tonumber(tokens) + elapsed * rate)

            if tokens < 1 then
                redis.call('HSET', key, 'last', now)
                redis.call('HSET', key, 'tokens', tokens)
                redis.call('EXPIRE', key, 60)
                return 0
            end

            tokens = tokens - 1
            redis.call('HSET', key, 'last', now)
            redis.call('HSET', key, 'tokens', tokens)
            redis.call('EXPIRE', key, 60)
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private long capacity = 100;
    private long rate = 1;

    /**
     * 尝试为某个用户取一个令牌。
     * @return true = 允许本次操作；false = 被限流
     */
    public boolean tryAcquire(long userId) {
        Long r = redis.execute(
                ACQUIRE,
                List.of(RelationKeys.followRateLimit(userId)),
                String.valueOf(capacity),
                String.valueOf(rate));
        return r != null && r == 1L;
    }
}