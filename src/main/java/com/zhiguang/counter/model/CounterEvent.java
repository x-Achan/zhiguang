package com.zhiguang.counter.model;

import java.util.UUID;

/**
 * 计数增量事件：从同步写路径发往 Kafka，由消费端批量落库。
 *
 * @param eventId 事件唯一 ID（排障用；消费端幂等靠唯一键，不依赖它）
 * @param etype   实体类型，如 knowpost
 * @param eid     实体 ID
 * @param metric  指标名，如 like / fav（即 like_record.type）
 * @param uid     触发用户
 * @param delta   增量：+1 点赞 / -1 取消
 * @param ts      事件产生时间（毫秒）
 */
public record CounterEvent(
        String eventId,
        String etype,
        String eid,
        String metric,
        long uid,
        int delta,
        long ts
) {

    public static CounterEvent of(String etype, String eid, CounterType type, long uid, int delta) {
        return new CounterEvent(
                UUID.randomUUID().toString(),
                etype, eid, type.value(), uid, delta, System.currentTimeMillis());
    }
}