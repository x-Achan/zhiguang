package com.zhiguang;

import com.zhiguang.counter.mapper.LikeRecordMapper;
import com.zhiguang.counter.model.CounterType;
import com.zhiguang.counter.schema.CounterKeys;
import com.zhiguang.counter.task.CounterReconcileTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CounterReconcileTest {

    @Autowired
    CounterReconcileTask task;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    LikeRecordMapper likeRecordMapper;

    @Test
    void 对账应把漂移的计数修正回真值() {
        long postId = 367228634206965760L;      // 换成你库里真实存在的帖子
        CounterType type = CounterType.LIKE;
        String key = CounterKeys.knowpost(postId, type);

        // 1. 制造"存在但错"的派生值
        redis.opsForValue().set(key, "999");
        Long realCount = likeRecordMapper.countByPostAndType(postId, type.value());

        // 2. 触发对账
        boolean fixed = task.reconcileOne(postId, type);

        // 3. 断言：被修正，且值等于真相源
        assertTrue(fixed);
        assertEquals(String.valueOf(realCount), redis.opsForValue().get(key));
    }

    @Test
    void 对账不应把正确的值改错() {
        long postId = 367228634206965760L;
        CounterType type = CounterType.LIKE;
        task.reconcileOne(postId, type);                 // 先对齐
        assertFalse(task.reconcileOne(postId, type));    // 再对账应为"无偏差"
    }
}
