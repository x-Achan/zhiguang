package com.zhiguang.relation.api.dto;

public record RelationStatusResponse(
        boolean following,
        boolean followedBy,
        boolean mutual
) {
}