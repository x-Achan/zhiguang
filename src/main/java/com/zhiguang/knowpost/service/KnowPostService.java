package com.zhiguang.knowpost.service;

import com.zhiguang.knowpost.api.dto.KnowPostDetailResponse;
import com.zhiguang.knowpost.api.dto.KnowPostPatchRequest;

import java.util.List;

/**
 * 知文业务接口。
 */
public interface KnowPostService {

    long createDraft(long creatorId);

    void confirmContent(long creatorId, long id, String objectKey, String etag, Long size, String sha256);

    void updateMetadata(long creatorId, long id, KnowPostPatchRequest request);
    void publish(long creatorId, long id);

    void updateTop(long creatorId, long id, Boolean isTop);

    void updateVisibility(long creatorId, long id, String visible);

    void delete(long creatorId, long id);

    KnowPostDetailResponse getDetail(long id, Long currentUserIdNullable);
}