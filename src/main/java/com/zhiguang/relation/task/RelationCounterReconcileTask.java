package com.zhiguang.relation.task;

import com.zhiguang.counter.model.UserCounterType;
import com.zhiguang.counter.schema.UserCounterKeys;
import com.zhiguang.relation.mapper.RelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RelationCounterReconcileTask {
    /**
     * 差值修正：
     *
     * - Redis 不存在：返回 -1
     * - 当前值等于真实值：返回 0
     * - 当前值比真实值多，且差值小于容忍值：返回 -2
     * - 其他情况：按照差值修正
     */
    private static final DefaultRedisScript<Long>
            CORRECT_COUNTER = new DefaultRedisScript<>(
            """
            local v = redis.call('GET', KEYS[1])

            if not v then
                return -1
            end

            local current = tonumber(v)
            local truth = tonumber(ARGV[1])
            local tolerance = tonumber(ARGV[2])
            local diff = current - truth

            if diff == 0 then
                return 0
            end

            /*
             * 当前值比真实值多出来一点，
             * 可能是异步消息尚未完全处理完成，暂时不修正。
             */
            if diff > 0 and diff <= tolerance then
                return -2
            end

            local delta = truth - current

            redis.call('INCRBY', KEYS[1], delta)

            return delta
            """,
            Long.class
    );

    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final RelationMapper relationMapper;

    private int sampleSize = 200;

    private long tolerance = 1000;

    private static final String LOCK_KEY = "lock:relation:counter:reconcile";

    @Scheduled(fixedDelayString = "${relation.reconcile.interval-ms:300000}", initialDelayString = "${relation.reconcile.initial-delay-ms:30000}")
    public void reconcile() {
        RLock lock = redisson.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            doReconcile();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("关系计数对账被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void doReconcile() {
        List<String> samples = redis.opsForSet().randomMembers(UserCounterKeys.INDEX, sampleSize);
        if (samples == null || samples.isEmpty()) {
            return;
        }
        for (String sample : samples) {
            reconcileOne(sample);
        }
    }

    private void reconcileOne(String sample) {
        String[] parts = sample.split(":");
        if (parts.length != 2) {
            redis.opsForSet().remove(UserCounterKeys.INDEX, sample);
            return;
        }
        long userId;
        try {
            userId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            redis.opsForSet().remove(UserCounterKeys.INDEX, sample);
            return;
        }
        UserCounterType type;
        try {
            type = UserCounterType.fromValue(parts[1]);
        } catch (IllegalArgumentException e) {
            redis.opsForSet().remove(UserCounterKeys.INDEX, sample);
            return;
        }
        String key = UserCounterKeys.user(userId, type);

        /*
         * 缓存已过期，交给下一次读取时重建。
         */
        if (redis.opsForValue().get(key) == null) {
            redis.opsForSet().remove(UserCounterKeys.INDEX, sample);
            return;
        }
        long truthValue;
        if (type == UserCounterType.FOLLOWINGS) {
            truthValue = relationMapper.countFollowingActive(userId);
        } else {
            truthValue = relationMapper.countFollowerActive(userId);
        }
        Long result = redis.execute(CORRECT_COUNTER, List.of(key), String.valueOf(truthValue), String.valueOf(tolerance));
    }
}