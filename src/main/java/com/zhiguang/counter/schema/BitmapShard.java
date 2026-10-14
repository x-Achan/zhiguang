package com.zhiguang.counter.schema;

/**
 * 位图分片：把"谁对谁做了什么"的事实按用户 ID 分片存位图。
 *
 * <p><b>为什么要分片</b>：位图 key 的大小由最大偏移决定。如果所有用户共用一个位图，
 * 一个 ID=800 万的用户点了赞，这个 key 就得撑到 1MB；分片后每个 key 上限固定 4KB。</p>
 *
 * <p>分片大小是权衡：片太大 → key 少但单键重、易成热点；片太小 → key 多、管理成本高。</p>
 */
public final class BitmapShard {

    /** 每个分片的位数：32768 位 = 4096 字节 = 4KB */
    public static final int CHUNK_SIZE = 32_768;

    /** 用户 ID → 分片号 */
    public static long chunkOf(long userId) {
        return userId / CHUNK_SIZE;
    }

    /** 用户 ID → 分片内的位偏移 */
    public static long bitOf(long userId) {
        return userId % CHUNK_SIZE;
    }

    private BitmapShard() {
    }
}