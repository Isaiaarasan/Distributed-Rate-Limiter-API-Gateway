package com.ratelimiter.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis configuration.
 *
 * We configure two Redis templates:
 *  - StringRedisTemplate (blocking) → used in LuaScriptExecutor for atomic Lua script execution.
 *    Blocking calls are wrapped in Schedulers.boundedElastic() to avoid blocking event-loop threads.
 *  - ReactiveRedisTemplate (reactive) → used for any future reactive Redis operations.
 */
@Configuration
public class RedisConfig {

    /**
     * Blocking Redis template.
     * Lua scripts are executed via this template from a boundedElastic thread pool.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Reactive Redis template for non-blocking operations.
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());
    }
}
