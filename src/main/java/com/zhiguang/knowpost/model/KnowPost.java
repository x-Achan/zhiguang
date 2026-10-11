package com.zhiguang.knowpost.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowPost {
    private Long id;
    private Long tagId; // 主标签id
    /** JSON 字符串，示例：["java","编程"] */
    private String tags;
    private String title; //标题
    private String description; //摘要

    // content开头的五个就是绑定内容相关的
    private String contentUrl; // 内容访问 URL
    private String contentObjectKey; // 内容在 OSS 的 key
    private String contentEtag; //内容 ETag，OSS 返回的内容指纹，校验内容是否变化
    private Long contentSize; //文件大小
    private String contentSha256; // 内容的 SHA-256 哈希

    private Long creatorId; // 作者 ID
    private Boolean isTop; //置顶
    private String type; //知文类型
    private String visible; //可见性
    /** JSON 字符串，示例：["https://...","https://..."] */
    private String imgUrls; //知文的配图列表
    private String videoUrl; //视频 URL


    private String status; //状态：知文的生命周期
    private Instant createTime;
    private Instant updateTime;
    private Instant publishTime;
}