package com.zhiguang.relation.event;

/**
 * Outbox 相关的常量。
 */
public final class OutboxTopics {

    private OutboxTopics() {
    }

    /** Canal 将 outbox 表 INSERT 事件投递到的 Kafka 主题。 */
    public static final String CANAL_OUTBOX = "canal-outbox";

    /** outbox 表的 aggregate_type 取值：关注关系 */
    public static final String AGG_FOLLOWING = "following";
}
