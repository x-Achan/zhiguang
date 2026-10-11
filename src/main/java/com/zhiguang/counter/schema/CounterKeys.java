package com.zhiguang.counter.schema;

import com.zhiguang.counter.model.CounterType;

/**
 * 计数相关 Redis 键名集中定义。
 * 集中管理的目的：键名散落在业务代码里手拼字符串，拼错了不会报错，只会静默失效。
 */
public final class CounterKeys {

    private CounterKeys() {
    }


    /** 已缓存计数器的索引集合：成员形如 "12345:like"。用于对账抽样，避免 KEYS/SCAN 全库扫描。 */
    public static final String INDEX = "cnt:index";

    /** 通用格式：cnt:{实体类型}:{实体ID}:{指标} */
    public static String count(String entityType, String entityId, CounterType type) {
        return "cnt:" + entityType + ":" + entityId + ":" + type.value();
    }

    /** 知文维度计数键：cnt:knowpost:{postId}:like */
    public static String knowpost(long postId, CounterType type) {
        return count("knowpost", String.valueOf(postId), type);
    }

    /**
     * 索引条目：postId:type
     * 存复合值而不是完整 key，便于反解出查库所需的 postId 与 type。
     */
    public static String indexEntry(long postId, CounterType type) {
        return postId + ":" + type.value();
    }
}
