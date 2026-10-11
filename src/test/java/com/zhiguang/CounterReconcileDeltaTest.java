package com.zhiguang;

import com.zhiguang.counter.mapper.LikeRecordMapper;
import com.zhiguang.counter.model.CounterType;
import com.zhiguang.counter.schema.CounterKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 V3 最核心的设计决策：对账修正必须用【差值 INCRBY】，不能用【SET 覆盖】。
 *
 * <p>结论：本测试【能通过】说明差值修正是对的；
 * 如果你把修正那一行改成 {@code redis.opsForValue().set(key, String.valueOf(truthRead))}，
 * 测试会失败 —— 这就是它的价值：把"为什么不能用 SET"从口头说明变成可执行的断言。</p>
 *
 * <p>用一个专用的大 postId，避免污染真实帖子的数据。</p>
 */
@SpringBootTest
class CounterReconcileDeltaTest {

    /** 专用测试帖 ID，与真实数据隔离 */
    private static final long POST_ID = 999000000000000001L;
    private static final CounterType TYPE = CounterType.LIKE;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LikeRecordMapper likeRecordMapper;

    private String key() {
        return CounterKeys.knowpost(POST_ID, TYPE);
    }

    /** 每次用一个全新的 userId，避免撞 uk_user_post_type 唯一键 */
    private long newUserId() {
        return 8_000_000_000_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
    }

    @Test
    void 差值修正不应覆盖对账期间的并发点赞() {
        String key = key();

        // ① 建立基线：真相源有 1 条，Redis 与之一致
        likeRecordMapper.insert(newUserId(), POST_ID, TYPE.value());
        long truth0 = likeRecordMapper.countByPostAndType(POST_ID, TYPE.value());
        redis.opsForValue().set(key, String.valueOf(truth0));

        // ② 人为制造漂移：Redis 比真相源小 1（模拟"Redis 那次写失败了"）
        long current = truth0 - 1;
        redis.opsForValue().set(key, String.valueOf(current));

        // ③ 模拟对账的【读取阶段】：读出当前值和真相值，算出差值
        long truthRead = likeRecordMapper.countByPostAndType(POST_ID, TYPE.value());
        long delta = truthRead - current;

        // ④ 模拟"读取之后、修正之前"来了一次并发点赞（写明细 + Redis 自增）
        likeRecordMapper.insert(newUserId(), POST_ID, TYPE.value());   // 真相 +1
        redis.opsForValue().increment(key);                            // Redis +1

        // ⑤ 模拟对账的【修正阶段】：应用差值
        redis.opsForValue().increment(key, delta);
        //    ↑ 把这一行换成 set(key, String.valueOf(truthRead)) 就会覆盖掉第 ④ 步的并发点赞

        // ⑥ 断言：Redis 必须与真相源一致
        long truthNow = likeRecordMapper.countByPostAndType(POST_ID, TYPE.value());
        assertEquals(truthNow, Long.parseLong(redis.opsForValue().get(key)),
                "差值修正后 Redis 必须等于真相源；若用 SET 覆盖，此断言会失败");

        // 清理 Redis 痕迹
        redis.delete(key);
        redis.opsForSet().remove(CounterKeys.INDEX, CounterKeys.indexEntry(POST_ID, TYPE));
    }
}
