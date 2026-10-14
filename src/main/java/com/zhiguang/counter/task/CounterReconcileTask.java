package com.zhiguang.counter.task;
import com.zhiguang.counter.mapper.LikeRecordMapper;
import com.zhiguang.counter.model.CounterType;
import com.zhiguang.counter.schema.CounterKeys;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CounterReconcileTask {

// 原子修正脚本
    private static final DefaultRedisScript<Long> CORRECT_COUNTER = new DefaultRedisScript<>("""
            local v = redis.call('GET', KEYS[1])
            if not v then
                return -1
            end
            local cur = tonumber(v)
            local truth = tonumber(ARGV[1])
            local tol = tonumber(ARGV[2])
            local diff = cur - truth
            if diff == 0 then
                return 0
            end
            if diff > 0 and diff <= tol then
                return -2
            end
            local delta = truth - cur
            redis.call('INCRBY', KEYS[1], delta)
            return delta
            """, Long.class);

    // 单次抽样数量
    private static final int SAMPLE_SIZE = 200;
    // 在途容忍上限：差值小于它视为"异步落库还没追上
    private long tolerance = 1000;
    // 分布式锁key
    private static final String LOCK_KEY = "lock:counter-reconcile";

    private final StringRedisTemplate redis;
    private final LikeRecordMapper likeRecordMapper;
    private final RedissonClient redisson;

    // 定时从Redis表中取出索引表，对账
    @Scheduled(fixedDelayString = "${counter.reconcile.interval-ms:300000}")
    public void reconcile(){
        RLock lock = redisson.getLock(LOCK_KEY);
        try {
            // 抢锁：对账是周期性任务，抢不到就跳过本轮，下一轮会补 → 不需要排队
            boolean isLock = lock.tryLock(0, TimeUnit.SECONDS);
            if(isLock){
                // 执行重建
                doReconcile();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    public void doReconcile(){

        List<String> samples = redis.opsForSet().randomMembers(CounterKeys.INDEX, SAMPLE_SIZE);
        if(samples == null || samples.isEmpty()) return;
        for (String sample : samples) {
            String[] parts = sample.split(":");
            long postId = Long.parseLong(parts[0]);
            CounterType type = CounterType.fromValue(parts[1]);

            String key = CounterKeys.knowpost(postId,type);

            // 如果key已经失效了，从索引表中移除
            if(redis.opsForValue().get(key) == null){
                redis.opsForSet().remove(CounterKeys.INDEX,sample);
                continue;
            }
            reconcileOne(postId,type);
        }

    }

    // 对账单个计数器，修正偏差
    public void reconcileOne(long postId, CounterType type){
        String key = CounterKeys.knowpost(postId,type);
        long truthValue = likeRecordMapper.countByPostAndType(postId, type.value());
        // 执行lua脚本，修正
        Long r = redis.execute(
                CORRECT_COUNTER,
                List.of(key),
                String.valueOf(truthValue),
                String.valueOf(tolerance));

    }
}


