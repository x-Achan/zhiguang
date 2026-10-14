package com.zhiguang.relation.cache;

import com.zhiguang.relation.mapper.RelationMapper;
import com.zhiguang.relation.schema.RelationKeys;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RelationListCacheService {

    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final RelationMapper relationMapper;

    private final int pageSize = 30;
    private final long listTtlSeconds = 7200;
    private static final double INIT_SCORE = Double.POSITIVE_INFINITY;
    private static final double END_SCORE = Double.NEGATIVE_INFINITY;

    public List<Long> readFollowingIds(long userId, int limit, int offset) {
        String key = RelationKeys.followingList(userId);
        String lockKey = RelationKeys.followingRebuildLock(userId);
        return readPage(key, lockKey, limit, offset, true, userId);
    }

    public List<Long> readFollowerIds(long userId, int limit, int offset) {
        String key = RelationKeys.followerList(userId);
        String lockKey = RelationKeys.followerRebuildLock(userId);
        return readPage(key, lockKey, limit, offset, false, userId);
    }

    private List<Long> readPage(String zsetKey, String lockKey, int limit, int offset, boolean following, long userId) {
        int requiredCount = offset + limit;
        if (!Boolean.TRUE.equals(redis.hasKey(zsetKey))) {
            initializeFirstPage(zsetKey, following, userId);
        }

        if (!hasEnoughData(zsetKey, requiredCount) && !hasEndMarker(zsetKey)) {
            RLock lock = redisson.getLock(lockKey);
            boolean locked = false;
            try {
                locked = lock.tryLock(2, 15, TimeUnit.SECONDS);
                if (!locked) {
                    return queryDatabasePage(following, userId, limit, offset);
                }
                if (!hasEnoughData(zsetKey, requiredCount) && !hasEndMarker(zsetKey)) {
                    loadUntil(zsetKey, following, userId, requiredCount);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return queryDatabasePage(following, userId, limit, offset);
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
        return readFromZSet(zsetKey, limit, offset);
    }


    private void initializeFirstPage(String zsetKey, boolean following, long userId) {
        redis.delete(zsetKey);
        redis.opsForZSet().add(zsetKey, RelationKeys.INIT_MEMBER, INIT_SCORE);
        List<Map<String, Object>> rows = queryDatabaseRows(following, userId, pageSize, 0);

        appendRows(zsetKey, rows);

        if (rows.size() < pageSize) {
            addEndMarker(zsetKey);
        }
        refreshTtl(zsetKey);
    }

    /**
     * 从数据库分页加载，直到满足目标数量。
     */
    private void loadUntil(String zsetKey, boolean following, long userId, int requiredCount) {
        int cachedCount = getCachedDataCount(zsetKey);

        while (cachedCount < requiredCount && !hasEndMarker(zsetKey)) {

            List<Map<String, Object>> rows = queryDatabaseRows(following, userId, pageSize, cachedCount);

            if (rows.isEmpty()) {
                addEndMarker(zsetKey);
                break;
            }
            appendRows(zsetKey, rows);
            cachedCount += rows.size();
            if (rows.size() < pageSize) {
                addEndMarker(zsetKey);
                break;
            }
        }
        refreshTtl(zsetKey);
    }

    /**
     * 从 Redis ZSet 读取指定页。
     *
     * INIT_MEMBER 占据倒序下标 0，
     * 所以真实用户从下标 1 开始。
     */
    private List<Long> readFromZSet(String zsetKey, int limit, int offset) {
        long start = offset + 1L;
        long end = (long) offset + limit;

        Set<String> members = redis.opsForZSet().reverseRange(zsetKey, start, end);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = new ArrayList<>(members.size());
        for (String member : members) {
            if (RelationKeys.INIT_MEMBER.equals(member) || RelationKeys.END_MEMBER.equals(member)) {
                continue;
            }
            userIds.add(Long.parseLong(member));
        }
        return userIds;
    }

    private boolean hasEnoughData(String zsetKey, int requiredCount) {
        return getCachedDataCount(zsetKey) >= requiredCount;
    }

    private int getCachedDataCount(String zsetKey) {
        Long size = redis.opsForZSet().zCard(zsetKey);

        if (size == null || size == 0) {
            return 0;
        }
        int count = size.intValue() - 1;
        if (hasEndMarker(zsetKey)) {
            count--;
        }
        return Math.max(count, 0);
    }

    private boolean hasEndMarker(String zsetKey) {
        Double score = redis.opsForZSet().score(zsetKey, RelationKeys.END_MEMBER);
        return score != null;
    }
    private void appendRows(String zsetKey, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();

        for (Map<String, Object> row : rows) {
            Object userIdValue = row.get("userId");
            Object createdAtValue = row.get("createdAt");

            if (!(userIdValue instanceof Number userIdNumber)) {
                continue;
            }
            long userId = userIdNumber.longValue();
            double score = toScore(createdAtValue);

            tuples.add(ZSetOperations.TypedTuple.of(String.valueOf(userId), score));
        }

        if (!tuples.isEmpty()) {
            redis.opsForZSet().add(zsetKey, tuples);
        }
    }

    private void addEndMarker(String zsetKey) {
        redis.opsForZSet().add(zsetKey, RelationKeys.END_MEMBER, END_SCORE);}


    private List<Map<String, Object>> queryDatabaseRows(boolean following, long userId, int limit, int offset) {
        if (following) {
            return relationMapper.listFollowingRows(userId, limit, offset);
        }
        return relationMapper.listFollowerRows(userId, limit, offset);
    }

    private List<Long> queryDatabasePage(boolean following, long userId, int limit, int offset) {
        List<Map<String, Object>> rows = queryDatabaseRows(following, userId, limit, offset);
        List<Long> ids = new ArrayList<>(rows.size());

        for (Map<String, Object> row : rows) {
            Object value = row.get("userId");

            if (value instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    private void refreshTtl(String key) {
        redis.expire(key, Duration.ofSeconds(listTtlSeconds));
    }

    private double toScore(Object value) {
        if (value instanceof Date date) {
            return date.getTime();
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return System.currentTimeMillis();
    }

    /**
     * 关注/取关消息到达后，直接删除部分缓存。
     *
     * 部分缓存不能直接 ZADD，否则新关注会改变所有分页的位置。
     * 删除后由下一次读取懒重建。
     */
    public void invalidateFollowCache(long fromUserId, long toUserId) {
        redis.delete(RelationKeys.followingList(fromUserId));
        redis.delete(RelationKeys.followerList(toUserId));
    }
}