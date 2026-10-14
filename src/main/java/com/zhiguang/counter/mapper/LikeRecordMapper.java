package com.zhiguang.counter.mapper;

import com.zhiguang.counter.mapper.dto.LikeRecordRow;
import com.zhiguang.counter.mapper.dto.PostCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点赞/收藏明细表访问。
 * 幂等由 uk_user_post_type 唯一键保证，不在这里做查询判断。
 */
@Mapper
public interface LikeRecordMapper {

    /**
     * 插入一条明细。重复时数据库抛唯一键冲突，
     * 由 mybatis-spring 翻译为 DuplicateKeyException，调用方捕获即为"已点过"。
     */
    int insert(@Param("userId") long userId,
               @Param("postId") long postId,
               @Param("type") String type);

    /** 删除一条明细，返回删除行数（0 = 本来就没点过） */
    int delete(@Param("userId") long userId,
               @Param("postId") long postId,
               @Param("type") String type);

    /** 单帖计数：从真相源算出真实数量（懒重建用） */
    long countByPostAndType(@Param("postId") long postId, @Param("type") String type);

    /**
     * 批量计数：一次 GROUP BY 拿回多个帖子的计数。
     * ★ 批量重建必须用它，不能循环调 countByPostAndType（那就退化成 N 次查询了）。
     */
    List<PostCountRow> countByPostIdsAndType(@Param("postIds") List<Long> postIds,
                                             @Param("type") String type);

    /**
     * 批量查询：这批帖子中，该用户点过赞/收藏的有哪些。
     * 用于消除 Feed 的 N+1（一次 SQL 替 N 次）。
     */
    List<Long> findPostIdsByUserAndType(@Param("userId") long userId,
                                        @Param("postIds") List<Long> postIds,
                                        @Param("type") String type);

    /**
     * 批量插入点赞明细（写聚合落库用）：一次多值 INSERT 替 N 次单条 INSERT。
     *
     * <p><b>★ 为什么这里可以用 INSERT IGNORE</b>（单条插入路径我们刻意不用它）：
     * 这里的值全部由服务端生成（两个 long + 一个枚举串），不存在用户输入被截断的风险，
     * 唯一可能触发的错误就是唯一键冲突 —— 而冲突恰好需要被忽略（Kafka 重复投递）。</p>
     */
    int batchInsertIgnore(@Param("rows") List<LikeRecordRow> rows);

}
