package com.ratelimiter.gateway.ratelimiter.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Component
@Slf4j
public class LuaScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List<Long>> tokenBucketScript;
    private final DefaultRedisScript<List<Long>> slidingWindowScript;

    @SuppressWarnings("unchecked")
    public LuaScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
        this.tokenBucketScript.setResultType((Class<List<Long>>) (Class<?>) List.class);

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/sliding_window.lua")));
        this.slidingWindowScript.setResultType((Class<List<Long>>) (Class<?>) List.class);

        log.info("Lua scripts loaded: token_bucket.lua, sliding_window.lua");
    }

    public Mono<long[]> executeTokenBucket(String key, long capacity, double refillRate, long now) {
        return Mono.fromCallable(() -> {
            List<Long> result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(now),
                    "1"
            );

            if (result == null || result.size() < 2) {
                log.warn("Token bucket Lua script returned unexpected result: {}. Fail-open: allowing.", result);
                return new long[]{1L, Math.max(0, capacity - 1)};
            }

            return new long[]{result.get(0), result.get(1)};
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(ex -> {
            log.error("Redis error during token bucket check for key [{}]: {}. Fail-open.", key, ex.getMessage());
            return Mono.just(new long[]{1L, 0L});
        });
    }

    public Mono<long[]> executeSlidingWindow(String key, long maxRequests, long windowSeconds,
                                              long now, String requestId) {
        return Mono.fromCallable(() -> {
            List<Long> result = redisTemplate.execute(
                    slidingWindowScript,
                    List.of(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowSeconds),
                    String.valueOf(now),
                    requestId
            );

            if (result == null || result.size() < 2) {
                log.warn("Sliding window Lua script returned unexpected result: {}. Fail-open: allowing.", result);
                return new long[]{1L, Math.max(0, maxRequests - 1)};
            }

            return new long[]{result.get(0), result.get(1)};
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(ex -> {
            log.error("Redis error during sliding window check for key [{}]: {}. Fail-open.", key, ex.getMessage());
            return Mono.just(new long[]{1L, 0L});
        });
    }
}
