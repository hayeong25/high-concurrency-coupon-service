package com.coupon.concurrency.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Redis 및 Redisson 설정 클래스
 * 분산 락을 위한 RedissonClient를 구성한다.
 */
@Configuration
@Profile("!test")
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * Redisson 클라이언트를 생성한다.
     * Single Server 모드로 구성되며, 분산 락에 사용된다.
     *
     * @return RedissonClient 인스턴스
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setConnectionMinimumIdleSize(10)
                .setConnectionPoolSize(64);
        return Redisson.create(config);
    }
}