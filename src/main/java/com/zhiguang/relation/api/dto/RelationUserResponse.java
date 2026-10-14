package com.zhiguang.relation.api.dto;

import java.time.LocalDate;

/** 关注和粉丝列表使用的公开用户资料，刻意不包含邮箱、手机号和密码相关字段。 */
public record RelationUserResponse(
        long id,
        String nickname,
        String avatar,
        String bio,
        String zgId,
        String gender,
        LocalDate birthday,
        String school,
        String tagsJson
) {
}
