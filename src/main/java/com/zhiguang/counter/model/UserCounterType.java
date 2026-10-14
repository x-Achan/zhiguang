package com.zhiguang.counter.model;

/**
 * 用户维度的计数指标。
 *
 * <p>和 {@link CounterType}（帖子维度：点赞/收藏）区分开：
 * 帖子维度的实体是"一篇知文"，用户维度的实体是"一个用户"。</p>
 */
public enum UserCounterType {

    /** 我关注了多少人 */
    FOLLOWINGS("followings"),

    /** 多少人关注了我 */
    FOLLOWERS("followers");

    private final String value;

    UserCounterType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static UserCounterType fromValue(String value) {
        for (UserCounterType t : values()) {
            if (t.value().equals(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown UserCounterType: " + value);
    }
}