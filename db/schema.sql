-- MySQL 8.0 schema for ZhiGuang authentication service

CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                     phone VARCHAR(32) NULL,
                                     email VARCHAR(128) NULL,
                                     password_hash VARCHAR(128) NULL,
                                     nickname VARCHAR(64) NOT NULL,
                                     avatar TEXT NULL,
                                     bio VARCHAR(512) NULL,
                                     zg_id VARCHAR(64) NULL,
                                     gender VARCHAR(16) NULL,
                                     birthday DATE NULL,
                                     school VARCHAR(128) NULL,
                                     tags_json JSON NULL,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     PRIMARY KEY (id),
                                     UNIQUE KEY uk_users_phone (phone),
                                     UNIQUE KEY uk_users_email (email),
                                     UNIQUE KEY uk_users_zg_id (zg_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_logs (
                                          id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                          user_id BIGINT UNSIGNED NULL,
                                          identifier VARCHAR(128) NOT NULL,
                                          channel VARCHAR(32) NOT NULL,
                                          ip VARCHAR(45) NULL,
                                          user_agent VARCHAR(512) NULL,
                                          status VARCHAR(16) NOT NULL,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          PRIMARY KEY (id),
                                          KEY ix_login_logs_user_created_at (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 知文（KnowPost）主表
-- 说明：
-- - id 使用雪花算法在业务层生成（非自增）；
-- - tags、img_urls 使用 JSON 存储，兼容多标签/多图片；
-- - content 存储在 OSS，仅记录 URL 与校验信息；
-- - 一期类型仅 image_text，可扩展；
-- - 状态包含草稿/审核中/已发布，预留 rejected/deleted；
CREATE TABLE IF NOT EXISTS know_posts (
                                          id BIGINT UNSIGNED NOT NULL,
                                          tag_id BIGINT UNSIGNED NULL COMMENT '主分类/内容分类ID',
                                          tags JSON NULL COMMENT '标签名数组，例如 ["java","编程"]',
                                          title VARCHAR(256) NULL,
                                          description VARCHAR(50) NULL COMMENT '摘要/描述，最多50字',
                                          content_url TEXT NULL COMMENT '正文存储于OSS的访问URL或签名URL',
                                          content_object_key VARCHAR(512) NULL COMMENT 'OSS对象Key',
                                          content_etag VARCHAR(128) NULL COMMENT 'OSS ETag（用于校验）',
                                          content_size BIGINT UNSIGNED NULL COMMENT '正文字节大小',
                                          content_sha256 CHAR(64) NULL COMMENT '正文SHA-256哈希（hex）',
                                          creator_id BIGINT UNSIGNED NOT NULL,
                                          is_top TINYINT(1) NOT NULL DEFAULT 0,
                                          type VARCHAR(32) NOT NULL DEFAULT 'image_text',
                                          visible VARCHAR(32) NOT NULL DEFAULT 'public',
                                          img_urls JSON NULL COMMENT '图片URL数组或对象数组',
                                          video_url TEXT NULL COMMENT '视频URL（一期不使用）',
                                          status VARCHAR(16) NOT NULL DEFAULT 'draft',
                                          create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          publish_time TIMESTAMP NULL DEFAULT NULL,
                                          PRIMARY KEY (id),
                                          KEY ix_know_posts_creator_ct (creator_id, create_time),
                                          KEY ix_know_posts_status_ct (status, create_time),
                                          KEY ix_know_posts_tag_ct (tag_id, create_time),
                                          KEY ix_know_posts_top_ct (is_top, create_time),
                                          KEY ix_know_posts_creator_status_pub (creator_id, status, publish_time),
                                          CONSTRAINT fk_know_posts_creator FOREIGN KEY (creator_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS outbox (
                                      id BIGINT UNSIGNED NOT NULL,
                                      aggregate_type VARCHAR(64) NOT NULL,
                                      aggregate_id BIGINT UNSIGNED NULL,
                                      type VARCHAR(64) NOT NULL,
                                      payload JSON NOT NULL,
                                      created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                      PRIMARY KEY (id),
                                      KEY ix_outbox_agg (aggregate_type, aggregate_id),
                                      KEY ix_outbox_ct (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS following (
                                         id BIGINT UNSIGNED NOT NULL,
                                         from_user_id BIGINT UNSIGNED NOT NULL,
                                         to_user_id BIGINT UNSIGNED NOT NULL,
                                         rel_status TINYINT NOT NULL DEFAULT 1,
                                         created_at DATETIME(3) NOT NULL,
                                         updated_at DATETIME(3) NOT NULL,
                                         PRIMARY KEY (id),
                                         UNIQUE KEY uk_from_to (from_user_id, to_user_id),
                                         KEY idx_from_created (from_user_id, created_at, to_user_id, rel_status),
                                         KEY idx_to (to_user_id, from_user_id, rel_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS follower (
                                        id BIGINT UNSIGNED NOT NULL,
                                        to_user_id BIGINT UNSIGNED NOT NULL,
                                        from_user_id BIGINT UNSIGNED NOT NULL,
                                        rel_status TINYINT NOT NULL DEFAULT 1,
                                        created_at DATETIME(3) NOT NULL,
                                        updated_at DATETIME(3) NOT NULL,
                                        PRIMARY KEY (id),
                                        UNIQUE KEY uk_to_from (to_user_id, from_user_id),
                                        KEY idx_to_created (to_user_id, created_at, from_user_id, rel_status),
                                        KEY idx_from (from_user_id, to_user_id, rel_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



-- ① 计数直接放主表上（土，但读路径免费）
ALTER TABLE know_posts
    ADD COLUMN like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    ADD COLUMN fav_count  INT NOT NULL DEFAULT 0 COMMENT '收藏数';

-- ② 点赞明细表：靠唯一键判重（这就是"真相源"）
CREATE TABLE IF NOT EXISTS like_record (
                                           id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                           user_id    BIGINT UNSIGNED NOT NULL,
                                           post_id    BIGINT UNSIGNED NOT NULL,
                                           type       VARCHAR(16) NOT NULL COMMENT 'like / fav',
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           PRIMARY KEY (id),
                                           UNIQUE KEY uk_user_post_type (user_id, post_id, type),   -- ★ 幂等的关键
                                           KEY ix_post_type (post_id, type),                       -- 按帖子查
                                           KEY ix_user_type_created (user_id, type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ① 给 following 补"反向查询 + 有序分页 + 覆盖索引"
--    Route C 走单表，"谁关注了我"这个查询必须靠它，否则 ORDER BY created_at 会 filesort
ALTER TABLE following
    ADD KEY idx_to_created (to_user_id, created_at, from_user_id, rel_status);

-- ② outbox.id 改成数据库自增
--    原因：轮询式 relay 要用 id 做游标，而参考实现用 ThreadLocalRandom 生成随机 id，
--    非单调 → 游标式轮询会漏数据（id 比游标小但插入更晚的记录永远读不到）
ALTER TABLE outbox
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;