package com.zhiguang.counter.service;

import com.zhiguang.counter.model.UserCounterType;

import java.util.function.LongSupplier;

/**
 * 用户维度计数服务。
 *
 * <p><b>它只负责"键 + TTL + 索引登记"</b>，不负责"真值怎么算"——
 * 因为"粉丝数怎么统计"是关系模块的知识，计数模块不该反过来依赖它。
 * 所以读取时把"回源方法"作为参数传进来（依赖倒置）。</p>
 */
public interface UserCounterService {

    /**
     * 读取某个用户某个指标的计数。
     *
     * <p>Redis 命中直接返回；未命中则调用 {@code truthSupplier} 回源，
     * 写回后返回。<b>"0" 也算命中</b>（防穿透）。</p>
     *
     * @param truthSupplier 从真相源算出真值的方法，只在未命中时调用
     */
    long getCount(long userId, UserCounterType type, LongSupplier truthSupplier);

    /**
     * 把真值写回 Redis（带抖动 TTL + 对账索引登记）。
     * 消费端重建计数时直接调它。
     */
    void writeThrough(long userId, UserCounterType type, long value);
}