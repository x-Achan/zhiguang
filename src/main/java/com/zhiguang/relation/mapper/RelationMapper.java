package com.zhiguang.relation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 关注关系表（单表）的数据访问。
 *
 * <p><b>Route C 只有 following 一张表</b>：两个查询方向都查它，
 * 靠 idx_from_created / idx_to_created 两个索引分别服务。</p>
 */
@Mapper
public interface RelationMapper {

    /**
     * 尝试插入一条关注关系（行不存在时才会成功）。
     *
     * @return 1 = 确实插入了新行；0 = 行已存在（可能 rel_status=1 也可能 =0）
     */
    int insertFollowingIfAbsent(@Param("id") long id,
                                @Param("fromUserId") long fromUserId,
                                @Param("toUserId") long toUserId);

    /**
     * 复活一条被取关过的关系（只在 rel_status=0 时生效）。
     *
     * @return 1 = 确实复活了一条；0 = 本来就没有这行，或它已经是 rel_status=1
     */
    int reviveFollowing(@Param("fromUserId") long fromUserId,
                        @Param("toUserId") long toUserId);

    /**
     * 取关（逻辑删除，只在 rel_status=1 时生效）。
     *
     * @return 1 = 确实取消了；0 = 本来就没关注
     */
    int cancelFollowing(@Param("fromUserId") long fromUserId,
                        @Param("toUserId") long toUserId);

    /** 是否已关注 */
    int existsFollowing(@Param("fromUserId") long fromUserId,
                        @Param("toUserId") long toUserId);

    /**
     * 关注列表回填用：按关注时间倒序。
     * 返回的 Map 有 userId / createdAt 两个 key。
     */
    List<Map<String, Object>> listFollowingRows(@Param("fromUserId") long fromUserId,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    /** 粉丝列表回填用：按关注时间倒序。 */
    List<Map<String, Object>> listFollowerRows(@Param("toUserId") long toUserId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    /** 对账用：我的有效关注数 */
    long countFollowingActive(@Param("fromUserId") long fromUserId);

    /** 对账用：我的有效粉丝数 */
    long countFollowerActive(@Param("toUserId") long toUserId);

    List<Long> listRelationUserIds(
            @Param("limit") int limit
    );

    List<Map<String, Object>> listAllFollowingRows(
            @Param("fromUserId") long fromUserId
    );

    List<Map<String, Object>> listAllFollowerRows(
            @Param("toUserId") long toUserId
    );
}