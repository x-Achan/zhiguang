package com.zhiguang.counter;

import com.zhiguang.counter.model.CounterType;
import com.zhiguang.counter.service.CounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@SpringBootTest
class CounterBenchmarkTest {

    @Autowired
    private CounterService counterService;

    /** 改成你数据库里真实存在的一篇已发布知文 ID */
    private static final long POST_ID = 367228634206965760L;
    private static final int THREADS = 200;

    /**
     * 用户 ID 的间隔。
     * <p>★ 这是一个实验开关，用来验证"唯一索引 gap 锁竞争"：</p>
     * <ul>
     *   <li>{@code 1}  → 200 个连续 user_id，全部落在同一个索引 gap 里（最坏情况，测出来是错的）</li>
     *   <li>{@code 100000} → 200 个分散 user_id，模拟真实流量（不同用户 ID 天然分散）</li>
     * </ul>
     */
    private static final long UID_STEP = 100000L;

    @Test
    void benchmarkHotRowLike() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Long> costs = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < THREADS; i++) {
            final long uid = 900000L + i * UID_STEP;   // 不同用户；步长可调（见 UID_STEP 注释）
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();                     // 等所有线程就位，同时开跑
                    long t0 = System.nanoTime();
                    counterService.toggle(CounterType.LIKE, POST_ID, uid, true);
                    costs.add((System.nanoTime() - t0) / 1_000_000);   // ms
                } catch (Exception e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
        }

        ready.await();
        long wall0 = System.currentTimeMillis();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.MINUTES);
        long wall = System.currentTimeMillis() - wall0;

        Collections.sort(costs);
        System.out.println("========== 压测结果 ==========");
        System.out.println("线程数      : " + THREADS);
        System.out.println("UID_STEP    : " + UID_STEP + (UID_STEP == 1L ? "（连续 ID，同一索引 gap）" : "（分散 ID）"));
        System.out.println("成功        : " + costs.size());
        System.out.println("失败        : " + errors.size() + " " + errors.stream().limit(3).toList());
        System.out.println("总耗时      : " + wall + " ms");
        if (!costs.isEmpty()) {
            System.out.println("p50         : " + costs.get(costs.size() / 2) + " ms");
            System.out.println("p99         : " + costs.get((int) (costs.size() * 0.99)) + " ms");
            System.out.println("max         : " + costs.get(costs.size() - 1) + " ms");
        }
        System.out.println("==============================");
    }
}
