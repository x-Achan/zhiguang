package com.zhiguang.relation.api.dto;

/** 用户关系模块提供的两个权威计数。 */
public record RelationCountsResponse(long followings, long followers) {
}
