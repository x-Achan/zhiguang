package com.zhiguang.counter.api.dto;

/**
 * 点赞/收藏操作的统一响应。
 *
 * @param type    行为类型：like / fav
 * @param active  操作完成后是否处于"已点赞/已收藏"状态（等于请求的意图）
 * @param count   操作后的权威计数（前端直接覆盖，无需再查一次）
 * @param changed 本次请求是否真的改变了状态（false = 幂等命中，什么也没做）
 */
public record CounterActionResponse(
        String type,
        boolean active,
        long count,
        boolean changed
) {
}