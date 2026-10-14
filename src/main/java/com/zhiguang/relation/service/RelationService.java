package com.zhiguang.relation.service;

import com.zhiguang.relation.api.dto.RelationCountsResponse;
import com.zhiguang.relation.api.dto.RelationStatusResponse;
import com.zhiguang.relation.api.dto.RelationUserResponse;

import java.util.List;

/**
 * 用户关系服务。
 *
 * <p>返回值语义统一为 <b>"本次是否改变了状态"</b>（和点赞的 changed 一致）：
 * 重复关注返回 false，但<b>不是错误</b>——它是幂等的成功。</p>
 */
public interface RelationService {

    /**
     * 关注。
     *
     * @return true = 本次产生了新的关注（并已发出事件）；
     *         false = 之前就已经关注了（幂等，未产生事件）
     * @throws IllegalStateException    被限流
     * @throws IllegalArgumentException 关注自己
     */
    boolean follow(long fromUserId, long toUserId);

    /**
     * 取关。
     *
     * @return true = 本次确实取消了（并已发出事件）；
     *         false = 本来就没关注（幂等，未产生事件）
     */
    boolean unfollow(long fromUserId, long toUserId);

    /** 是否已关注 */
    boolean isFollowing(long fromUserId, long toUserId);

    /** 查询当前用户和目标用户之间的完整关系状态。 */
    RelationStatusResponse getStatus(long currentUserId, long targetUserId);

    /** 按最新关注时间倒序读取关注列表。 */
    List<RelationUserResponse> listFollowings(long userId, int limit, int offset);

    /** 按最新关注时间倒序读取粉丝列表。 */
    List<RelationUserResponse> listFollowers(long userId, int limit, int offset);

    /** 读取关注数和粉丝数；缓存缺失时从 MySQL 重建。 */
    RelationCountsResponse getCounts(long userId);
}
