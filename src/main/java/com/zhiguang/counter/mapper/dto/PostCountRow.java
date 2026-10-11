package com.zhiguang.counter.mapper.dto;

import lombok.Data;

/** 批量重建用的一行：某个帖子的计数值 */
@Data
public class PostCountRow {
    private Long postId;
    private Long cnt;
}