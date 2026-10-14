package com.zhiguang.counter.mapper.dto;

/** 批量落库用的一行点赞明细。 */
public record LikeRecordRow(long userId, long postId, String type) {
}