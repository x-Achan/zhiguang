package com.zhiguang.relation.outbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Outbox（本地消息表）的数据访问。
 *
 * <p>写入必须和业务写操作在同一个本地事务里 —— 这是 Outbox 模式能成立的前提。</p>
 */
@Mapper
public interface OutboxMapper {

    /**
     * 写入一条待投递事件。
     *
     * <p>id 由数据库自增生成，所以这里不传。</p>
     *
     * @param aggregateType 聚合类型，如 following
     * @param aggregateId   聚合 ID（following 行 id），可为空
     * @param type          事件类型，如 FollowCreated
     * @param payload       事件体（JSON 字符串）
     */
    int insert(@Param("aggregateType") String aggregateType,
               @Param("aggregateId") Long aggregateId,
               @Param("type") String type,
               @Param("payload") String payload);
}