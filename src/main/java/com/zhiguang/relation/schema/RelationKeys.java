package com.zhiguang.relation.schema;

/**
 * 关注模块的 Redis 键集中定义。
 * 集中管理的原因和 CounterKeys 一样：键名手拼出错不会报错，只会静默失效。
 */
public final class RelationKeys {

    private RelationKeys() {
    }

    /**
     * 关注操作的令牌桶：{@code rl:follow:{userId}}
     *
     * <p>Hash 结构，两个字段：
     * {@code last}（上次填充时间，秒）、{@code tokens}（当前可用令牌数）。</p>
     */
    public static String followRateLimit(long userId) {
        return "rl:follow:" + userId;
    }

    /**
     * 关注列表缓存：{@code uf:flws:{userId}}
     *
     * <p>ZSet，member = 被关注者 ID，score = 关注时间（毫秒）。
     * 用 ZSet 而不是 List 的关键原因：取关时要能从列表中间删掉一个人，
     * ZSet 的 ZREM 是 O(log N)，List 删除任意元素是 O(N)。</p>
     */
    public static String followingList(long userId) {
        return "uf:flws:" + userId;
    }

    /**
     * 粉丝列表缓存：{@code uf:fans:{userId}}
     * ZSet，member = 粉丝 ID，score = 关注时间（毫秒）。
     */
    public static String followerList(long userId) {
        return "uf:fans:" + userId;
    }

    /**
     * 事件消费幂等键：{@code dedup:rel:{eventId}}
     *
     * <p>★ 只用 eventId 一个维度，不再拼业务字段。
     * 参考实现拼的是 {@code type:from:to:id}，而取消关注事件的 id 传了 null，
     * 导致同一个用户的两轮"取关"撞上同一个幂等键 → 第二次被当成重复消息跳过 → 计数少减 1。</p>
     */
    public static String dedup(String eventId) {
        return "dedup:rel:" + eventId;
    }

    /**
     * Outbox 投递游标：{@code outbox:cursor:{aggregateType}}。
     * 轮询式 relay 用它记录"处理到哪个 id 了"。
     */
    public static String outboxCursor(String aggregateType) {
        return "outbox:cursor:" + aggregateType;
    }


    public static String followingRebuildLock(long userId) {
        return "lock:rel:following:" + userId;
    }

    public static String followerRebuildLock(long userId) {
        return "lock:rel:followers:" + userId;
    }

    public static final String INIT_MEMBER = "__INIT__";
    public static final String END_MEMBER = "__END__";


}