package com.zhiguang.relation.event;

import java.util.UUID;

/**
 * 关注关系变更事件。
 *
 * <p><b>★ 每个事件都必须带一个全局唯一的 eventId</b>，消费端的幂等键就用它。
 * 不要用 "type:from:to" 这类业务字段拼幂等键 —— 相同的一对用户可能反复关注/取关，
 * 那些字段组合出来的键会碰撞，导致真实事件被误判成重复消息丢弃。</p>
 *
 * @param eventId    事件唯一 ID（幂等键的唯一来源）
 * @param type       {@link #TYPE_FOLLOW_CREATED} / {@link #TYPE_FOLLOW_CANCELED}
 * @param fromUserId 关注发起者
 * @param toUserId   被关注者
 * @param relId      following 行的主键（排障用；取消关注时可为空）
 * @param ts         事件产生时间（毫秒）
 */
public record RelationEvent(
        String eventId,
        String type,
        long fromUserId,
        long toUserId,
        Long relId,
        long ts
) {

    public static final String TYPE_FOLLOW_CREATED = "FollowCreated";
    public static final String TYPE_FOLLOW_CANCELED = "FollowCanceled";

    public static RelationEvent followCreated(long fromUserId, long toUserId, long relId) {
        return new RelationEvent(
                UUID.randomUUID().toString(),
                TYPE_FOLLOW_CREATED,
                fromUserId, toUserId, relId, System.currentTimeMillis());
    }

    public static RelationEvent followCanceled(long fromUserId, long toUserId) {
        return new RelationEvent(
                UUID.randomUUID().toString(),
                TYPE_FOLLOW_CANCELED,
                fromUserId, toUserId, null, System.currentTimeMillis());
    }

    public boolean isCreated() {
        return TYPE_FOLLOW_CREATED.equals(type);
    }
}