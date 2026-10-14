package com.zhiguang.relation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiguang.knowpost.id.SnowflakeIdGenerator;
import com.zhiguang.relation.cache.RelationListCacheService;
import com.zhiguang.relation.event.OutboxTopics;
import com.zhiguang.relation.event.RelationEvent;
import com.zhiguang.relation.api.dto.RelationCountsResponse;
import com.zhiguang.relation.api.dto.RelationStatusResponse;
import com.zhiguang.relation.api.dto.RelationUserResponse;
import com.zhiguang.counter.model.UserCounterType;
import com.zhiguang.counter.service.UserCounterService;
import com.zhiguang.relation.mapper.RelationMapper;
import com.zhiguang.relation.outbox.OutboxMapper;
import com.zhiguang.relation.ratelimit.TokenBucketRateLimiter;
import com.zhiguang.relation.service.RelationService;
import com.zhiguang.user.domain.User;
import com.zhiguang.user.mapper.UserMapper;
import com.zhiguang.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 关系服务实现（Route C：单表 following + Outbox）。
 *
 * <p><b>写路径三步</b>：限流 → 写 following（幂等）→ 同一事务写 outbox。
 * 计数和缓存不在这里做，由消费端异步更新（Batch 4）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationServiceImpl implements RelationService {

    private final RelationMapper relationMapper;
    private final OutboxMapper outboxMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final TokenBucketRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final UserMapper userMapper;
    private final UserCounterService userCounterService;
    private final RelationListCacheService relationListCacheService;

    @Override
    @Transactional
    public boolean follow(long fromUserId, long toUserId) {
        if (fromUserId == toUserId) {
            throw new IllegalArgumentException("不能关注自己");
        }
        userService.findById(toUserId)
                .orElseThrow(() -> new IllegalArgumentException("被关注用户不存在"));

        // ① 限流：Redis 操作，不碰数据库
        if (!rateLimiter.tryAcquire(fromUserId)) {
            throw new IllegalStateException("操作过于频繁，请稍后再试");
        }

        // ② 关注（幂等）：
        //    先尝试插入（行不存在才会成功）；插不进说明行已存在，再尝试"复活"（仅 rel_status=0 时生效）。
        //    注意 || 的短路语义在这里正合适：插入成功了就不必再跑复活。
        long relId = idGenerator.nextId();
        boolean inserted = relationMapper.insertFollowingIfAbsent(relId, fromUserId, toUserId) > 0;
        boolean changed = inserted || relationMapper.reviveFollowing(fromUserId, toUserId) > 0;

        if (!changed) {
            // 之前就已经关注了 → 幂等返回，不产生事件（否则计数会被重复加）
            return false;
        }

        // ③ ★ 同一个本地事务里写 outbox，且绝不吞异常。
        //    Outbox 存在的唯一意义就是保证"写业务数据"和"记下待发事件"原子；
        //    如果 outbox 写失败还让关注成功，下游就永远不知道这次关注 —— 那要 Outbox 干什么？
        RelationEvent event = RelationEvent.followCreated(fromUserId, toUserId, inserted ? relId : null);
        outboxMapper.insert(
                OutboxTopics.AGG_FOLLOWING,
                relId,
                event.type(),
                toJson(event));

        log.info("[关注] from={} to={} relId={} eventId={}", fromUserId, toUserId, relId, event.eventId());
        return true;
    }

    @Override
    @Transactional
    public boolean unfollow(long fromUserId, long toUserId) {
        if (!rateLimiter.tryAcquire(fromUserId)) {
            throw new IllegalStateException("操作过于频繁，请稍后再试");
        }

        // 条件更新：只有 rel_status=1 时才会命中，所以 affected>0 精确表示"确实取消了"
        boolean changed = relationMapper.cancelFollowing(fromUserId, toUserId) > 0;
        if (!changed) {
            return false;   // 本来就没关注 → 幂等
        }

        RelationEvent event = RelationEvent.followCanceled(fromUserId, toUserId);
        outboxMapper.insert(
                OutboxTopics.AGG_FOLLOWING,
                null,
                event.type(),
                toJson(event));

        log.info("[取关] from={} to={} eventId={}", fromUserId, toUserId, event.eventId());
        return true;
    }

    @Override
    public boolean isFollowing(long fromUserId, long toUserId) {
        return relationMapper.existsFollowing(fromUserId, toUserId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public RelationStatusResponse getStatus(long currentUserId, long targetUserId) {
        boolean following = isFollowing(currentUserId, targetUserId);
        boolean followedBy = isFollowing(targetUserId, currentUserId);
        return new RelationStatusResponse(following, followedBy, following && followedBy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationUserResponse> listFollowings(long userId, int limit, int offset) {
        List<Long> userIds = relationListCacheService.readFollowingIds(userId, limit, offset);
        return toUsers(userIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RelationUserResponse> listFollowers(long userId, int limit, int offset) {
        List<Long> userIds = relationListCacheService.readFollowerIds(userId, limit, offset);
        return toUsers(userIds);
    }

    @Override
    public RelationCountsResponse getCounts(long userId) {
        long followings = userCounterService.getCount(
                userId,
                UserCounterType.FOLLOWINGS,
                () -> relationMapper.countFollowingActive(userId));
        long followers = userCounterService.getCount(
                userId,
                UserCounterType.FOLLOWERS,
                () -> relationMapper.countFollowerActive(userId));
        return new RelationCountsResponse(followings, followers);
    }



    private RelationUserResponse toRelationUser(User user) {
        return new RelationUserResponse(
                user.getId(), user.getNickname(), user.getAvatar(), user.getBio(),
                user.getZgId(), user.getGender(), user.getBirthday(), user.getSchool(), user.getTagsJson());
    }

    /** 序列化事件体。失败属于编码错误，必须抛出以触发事务回滚。 */
    private String toJson(RelationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("关系事件序列化失败: " + event.eventId(), e);
        }
    }

    private List<RelationUserResponse> toUsers(
            List<Long> userIds
    ) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> usersById = new HashMap<>();

        for (User user : userMapper.listByIds(userIds)) {
            usersById.put(user.getId(), user);
        }

        /*
         * 必须按照 Redis ZSet 中的顺序组装。
         */
        return userIds.stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toRelationUser)
                .toList();
    }
}
