package com.zhiguang.counter.service;

import com.zhiguang.counter.api.dto.CounterActionResponse;
import com.zhiguang.counter.model.CounterType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CounterService {

    /**
     * 点赞/收藏开关。
     *
     * @param type   行为类型
     * @param postId 知文 ID
     * @param userId 当前用户
     * @param on     true=点赞/收藏；false=取消
     * @return 操作后的状态与权威计数
     */
    CounterActionResponse toggle(CounterType type, long postId, long userId, boolean on);



    /** 读单个帖子的计数；key 缺失时从真相源懒重建 */
    long getCount(long postId, CounterType type);

    /** 强制从真相源重建并写回 Redis（调试 / 修复用） */
    long rebuild(long postId, CounterType type);

    /**
     * 批量查询：这批帖子里该用户已点赞/已收藏的 ID 集合。
     * 用于 Feed / 详情组装，避免逐条查询造成 N+1。
     */
//    Set<Long> findActionPostIds(CounterType type, long userId, List<Long> postIds);
    /**
     * 批量读计数：一次 MGET 拿回多个；缺失的用一次 GROUP BY 批量重建。
     * 给 Feed 用（避免 N+1）。
     */
    Map<Long, Long> getCounts(List<Long> postIds, CounterType type);
}
