package com.zhiguang.counter.service.impl;

import com.zhiguang.counter.api.dto.CounterActionResponse;
import com.zhiguang.counter.event.CounterEventProducer;
import com.zhiguang.counter.mapper.LikeRecordMapper;
import com.zhiguang.counter.model.CounterEvent;
import com.zhiguang.counter.model.CounterType;
import com.zhiguang.counter.schema.BitmapShard;
import com.zhiguang.counter.schema.CounterKeys;
import com.zhiguang.counter.service.CounterService;
import com.zhiguang.knowpost.mapper.KnowPostMapper;
import com.zhiguang.knowpost.model.KnowPost;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.startup.ContextRuleSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 计数服务：点赞/收藏
 *
 */
@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {
    // Lua脚本：判断 + 查询
    private static final DefaultRedisScript<Long> INCR_IF_EXISTS = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            return redis.call('INCRBY', KEYS[1], ARGV[1])
            """, Long.class);

    //位图原子切换：读当前位 → 与目标一致则返回 0（无变化）→ 否则置位并返回 1
    // KEYS[1]=位图键　ARGV[1]=位偏移　ARGV[2]=目标值(0/1)
    private static final DefaultRedisScript<Long> TOGGLE_BIT = new DefaultRedisScript<>("""
            local cur = redis.call('GETBIT', KEYS[1], ARGV[1])
            local want = tonumber(ARGV[2])
            if cur == want then
                return 0
            end
            redis.call('SETBIT', KEYS[1], ARGV[1], want)
            return 1
            """, Long.class);

    private final LikeRecordMapper likeRecordMapper;
    private final KnowPostMapper knowPostMapper;
    private final StringRedisTemplate redis;
    private final CounterEventProducer eventProducer;


    private long baseTtlSeconds = 7200; //基础 TTL
    private long jitterSeconds = 1200;  //抖动上限

    // 单飞锁容器，以缓存key为粒度，保证通过一个key在同一时刻只有一个线程回源
    private final ConcurrentHashMap<String,Object> flightLocks = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public CounterActionResponse toggle(CounterType type, long postId, long userId, boolean on) {
        // 1.前置校验
        KnowPost post = knowPostMapper.findById(postId);
        if(post == null || post.getStatus().equals("deleted")){
            throw new RuntimeException("帖子不存在或者被删除");
        }
        // lua脚本写位图
        boolean changed = toggleBitmap(type, postId, userId, on);

        if(!changed){
            return new CounterActionResponse(type.value(), on, getCount(postId, type), false);
        }

        String eid = String.valueOf(postId);
        try {
            // 发事件
            eventProducer.publish(CounterEvent.of("knowpost",eid,type,userId,on ? 1:-1));
        } catch (Exception e) {
            toggleBitmap(type, postId, userId, !on); // 补偿：把位改回去
            throw new IllegalStateException("点赞失败，请重试", e);
        }
        // 写cnt
        long count = applyDeltaAtomic(type, postId, on ? 1 : -1);
        return new CounterActionResponse(type.value(), on, count, true);
    }

    // Redis执行Lua脚本，实现自增INCE
    private long applyDeltaAtomic(CounterType type,long postId,long delta){
        String key = CounterKeys.knowpost(postId,type);
        Long after = redis.execute(INCR_IF_EXISTS, List.of(key), String.valueOf(delta));
        // Redis缺失或者发生飘逸为复数，从真相源重建
        if(after == null || after < 0){
            return rebuild(postId,type);// 写路径的重建是罕见操作，可以接受各自重建
        }
        return after;
    }

// 同步写路径的核心：一次位图 Lua，既完成判重，又给出"是否真的变化"
    private boolean toggleBitmap(CounterType type, long postId, long userId, boolean on) {
        String bmKey = CounterKeys.bitmap(
                type.value(),
                "knowpost",
                String.valueOf(postId),
                BitmapShard.chunkOf(userId));
        Long changed = redis.execute(
                TOGGLE_BIT,
                List.of(bmKey),
                String.valueOf(BitmapShard.bitOf(userId))
                , on ? "1" : "0");
        return changed != null && changed == 1L;
    }


    // 从 Redis中读，没有则重建，防止缓存击穿，只允许一个线程重建
    @Override
    public long getCount(long postId, CounterType type) {
        String key = CounterKeys.knowpost(postId,type);
        // 1.第一次检查
        String count = redis.opsForValue().get(key);
        if(count != null) {
            return Long.parseLong(count);
        }
        // 缓存击穿：single-flight 单飞锁
        Object lock = flightLocks.computeIfAbsent(key,k -> new Object());
        synchronized (lock){
            try {
                // 2.双重检查
                String value = redis.opsForValue().get(key);
                if(value != null) return Long.parseLong(value);
                // 3.重建
                return rebuild(postId,type);
            } finally {
                // 释放锁
                flightLocks.remove(key);
            }
        }
    }

    // 从真相源重建到 Reids cnt 中
    @Override
    public long rebuild(long postId, CounterType type) {
        long actual = likeRecordMapper.countByPostAndType(postId, type.value());
        // 缓存穿透：空值0也要写回到Redis
        // 缓存雪崩：抖动TTL
        long ttl = baseTtlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
        redis.opsForValue().set(
                CounterKeys.knowpost(postId, type),
                String.valueOf(actual),
                Duration.ofSeconds(ttl));
        // 登记索引，供对账抽样时使用
        redis.opsForSet().add(CounterKeys.INDEX,CounterKeys.indexEntry(postId,type));
        return actual;
    }

    private boolean insertRecord(CounterType type, long userId, long postId) {
        try {
            likeRecordMapper.insert(userId, postId, type.value());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
    private boolean deleteRecord(CounterType type, long userId, long postId) {
        return likeRecordMapper.delete(userId, postId, type.value()) > 0;
    }

    @Override
    public Map<Long, Long> getCounts(List<Long> postIds, CounterType type) {
        // 简单版：正确优先（复用 getCount 的懒重建）。
        // 等 Feed 做出来压测后，再优化成 MGET + 一次 GROUP BY 批量重建。
        Map<Long, Long> result = new LinkedHashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }
        for (Long id : postIds) {
            result.put(id, getCount(id, type));
        }
        return result;
    }

    // 读位图
    @Override
    public boolean isLiked(CounterType type, long postId, long userId) {
        String bmKey = CounterKeys.bitmap(
                type.value(), "knowpost", String.valueOf(postId), BitmapShard.chunkOf(userId));
        Boolean bit = redis.opsForValue().getBit(bmKey, BitmapShard.bitOf(userId));
        return Boolean.TRUE.equals(bit);
    }


}
