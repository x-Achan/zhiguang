package com.zhiguang.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 装配：复用 spring.data.redis.* 的配置，复用同一个 Redis 实例。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties props) {
        Config config = new Config();

        var server = config.useSingleServer()
                .setAddress("redis://" + props.getHost() + ":" + props.getPort())
                .setDatabase(props.getDatabase());

        // 我们的 Redis 配了密码，必须带上，否则连不上
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            server.setPassword(props.getPassword());
        }

        // ★ 看门狗超时：锁默认租期 = 该值，并且 Redisson 会每 1/3 租期自动续期。
        //   作用：对账任务执行时间超过租期时，锁不会被提前释放，
        //        避免出现"任务还在跑，锁已经没了，另一个实例又开始了"。
        config.setLockWatchdogTimeout(30_000L);

        return Redisson.create(config);
    }
}