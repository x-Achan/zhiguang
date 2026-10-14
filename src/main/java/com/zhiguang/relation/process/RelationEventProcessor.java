package com.zhiguang.relation.process;

import com.zhiguang.counter.model.UserCounterType;
import com.zhiguang.counter.service.UserCounterService;
import com.zhiguang.relation.cache.RelationListCacheService;
import com.zhiguang.relation.event.RelationEvent;
import com.zhiguang.relation.mapper.RelationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationEventProcessor {

    private final RelationMapper relationMapper;
    private final UserCounterService userCounterService;
    private final RelationListCacheService relationListCacheService;

    public void process(RelationEvent event) {
        /*
         * 因为当前 ZSet 只缓存部分分页数据，
         * 关注关系变化后直接删除两个相关缓存。
         * 下一次读取时，再从 MySQL 按分页重建。
         */
        if (event.isCreated() || RelationEvent.TYPE_FOLLOW_CANCELED.equals(event.type())) {
            relationListCacheService.invalidateFollowCache(event.fromUserId(), event.toUserId());
        } else {
            log.warn("忽略未知关系事件 type={}, eventId={}", event.type(), event.eventId());
            return;
        }

        /*
         * 计数根据数据库真实值刷新。
         * 不使用简单 INCR/DECR，避免重复消息和乱序消息导致计数错误。
         */
        long followings = relationMapper.countFollowingActive(event.fromUserId());
        long followers = relationMapper.countFollowerActive(event.toUserId());

        // 将数量写入 key并等级索引
        userCounterService.writeThrough(event.fromUserId(), UserCounterType.FOLLOWINGS, followings);
        userCounterService.writeThrough(event.toUserId(), UserCounterType.FOLLOWERS, followers);

        log.debug("[关系事件处理完成] eventId={}, type={}, from={}, to={}",
                event.eventId(),
                event.type(),
                event.fromUserId(),
                event.toUserId()
        );
    }
}