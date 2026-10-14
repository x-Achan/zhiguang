package com.zhiguang.counter.schema;

import com.zhiguang.counter.model.UserCounterType;

/**
 * 用户维度计数的 Redis 键。
 *
 * <p>格式：{@code ucnt:{userId}:{metric}}
 * 例：{@code ucnt:10001:followings}</p>
 */
public final class UserCounterKeys {

    private UserCounterKeys() {
    }

    /** 单个用户某个指标的计数键：ucnt:{userId}:{metric}
     * 我的关注量/粉丝量
     * */
    public static String user(long userId, UserCounterType type) {
        return "ucnt:" + userId + ":" + type.value();
    }

    /**
     * 对账抽样索引（Set）：成员形如 {@code 10001:followings}。
     * 作用和帖子计数里的 {@code cnt:index} 完全一样 —— 避免对账时用 KEYS 全库扫描。
     */
    public static final String INDEX = "ucnt:index";

    /** 索引条目：{@code userId:metric} */
    public static String indexEntry(long userId, UserCounterType type) {
        return userId + ":" + type.value();
    }
}