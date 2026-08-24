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

/**
 * Executes atomic Redis Lua scripts for rate limiting.
 *
 * WHY LUA SCRIPTS?
 * ─────────────────
 * Without atomicity, a race condition exists between two instances:
 *
 *   Instance A: reads tokens = 1
 *   Instance B: reads tokens = 1
 *   Instance A: sets tokens = 0, allows request
 *   Instance B: sets tokens = 0, allows request  ← BOTH allowed! Limit violated!
 *
 * Lua scripts execute as a single atomic unit inside Redis.
 * No other command can be interleaved during script execution.
 * This is what makes the rate limiter truly distributed-safe.
 *
 * IMPLEMENTATION NOTES:
 *   - Scripts are loaded once at startup (ClassPathResource → EVALSHA)
 *   - Blocking Redis calls are wrapped in Schedulers.boundedElastic()
 *     to avoid blocking the Netty event-loop threads
 *   - Fail-open policy: if Redis is unreachable, the request is allowed
 */
@Component
@Slf4j
public class LuaScriptExecutor {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final DefaultRedisScript<List> slidingWindowScript;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public LuaScriptExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // Load Token Bucket Lua script
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua")));
        this.tokenBucketScript.setResultType(List.class);

        // Load Sliding Window Lua script
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/sliding_window.lua")));
        this.slidingWindowScript.setResultType(List.class);

        log.info("Lua scripts loaded: token_bucket.lua, sliding_window.lua");
    }

    /**
     * Executes the Token Bucket Lua script atomically in Redis.
     *
     * Returns: long[]{allowed (1/0), remainingTokens}
     *
     * @param key        Redis key (unique per client+api combination)
     * @param capacity   maximum number of tokens in the bucket
     * @param refillRate tokens added per second (can be fractional)
     * @param now        current time in milliseconds
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Mono<long[]> executeTokenBucket(String key, long capacity, double refillRate, long now) {
        return Mono.fromCallable(() -> {
            List<Long> result = (List<Long>) redisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(now),
                    "1"                     // requested tokens (always 1)
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

    /**
     * Executes the Sliding Window Lua script atomically in Redis.
     *
     * Returns: long[]{allowed (1/0), remainingSlots}
     *
     * @param key           Redis key (ZSET key per client+api)
     * @param maxRequests   maximum requests allowed in the window
     * @param windowSeconds rolling window duration in seconds
     * @param now           current time in milliseconds
     * @param requestId     unique ID for this request (stored in the ZSET as member)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Mono<long[]> executeSlidingWindow(String key, long maxRequests, long windowSeconds,
                                              long now, String requestId) {
        return Mono.fromCallable(() -> {
            List<Long> result = (List<Long>) redisTemplate.execute(
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
