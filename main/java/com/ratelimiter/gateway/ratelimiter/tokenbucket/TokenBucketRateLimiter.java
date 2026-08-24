package com.ratelimiter.gateway.ratelimiter.tokenbucket;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicy;
import com.ratelimiter.gateway.ratelimiter.RateLimiter;
import com.ratelimiter.gateway.ratelimiter.RateLimitResult;
import com.ratelimiter.gateway.ratelimiter.redis.LuaScriptExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Token Bucket Rate Limiter
 * ──────────────────────────
 * Algorithm:
 *   - A bucket holds up to `capacity` tokens
 *   - Tokens refill at `refillRate` tokens/second
 *   - Each request consumes exactly 1 token
 *   - If bucket has ≥1 token → ALLOW (consume token)
 *   - If bucket is empty → DENY (return 429)
 *
 * Redis State (HASH):
 *   Key:  rl:tb:{clientKey}:{apiPath}
 *   Fields:
 *     tokens      → current token count (float)
 *     lastRefill  → timestamp of last refill in milliseconds
 *
 * All reads and writes to Redis state are atomic via the Lua script.
 *
 * Strengths:
 *   ✅ Allows burst traffic (full bucket at startup/reset)
 *   ✅ Smooth average rate (token refill prevents sustained overload)
 *   ✅ Simple to understand and explain in interviews
 *
 * Weaknesses:
 *   ⚠️ Can allow 2× rate at window boundaries (fixed by Token Bucket design, not an issue here)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBucketRateLimiter implements RateLimiter {

    private final LuaScriptExecutor luaScriptExecutor;

    @Override
    public Mono<RateLimitResult> isAllowed(String clientKey, String apiPath, RateLimitPolicy policy) {
        String redisKey = buildRedisKey(clientKey, apiPath);
        long   capacity   = policy.getBucketCapacity();
        double refillRate = policy.getRefillRate();
        long   now        = System.currentTimeMillis();

        log.debug("TokenBucket check → key={}, capacity={}, refillRate={}/s",
                redisKey, capacity, refillRate);

        return luaScriptExecutor.executeTokenBucket(redisKey, capacity, refillRate, now)
                .map(result -> {
                    boolean allowed   = result[0] == 1L;
                    long    remaining = result[1];

                    if (allowed) {
                        log.debug("TokenBucket ALLOW → key={}, remaining={}", redisKey, remaining);
                        return RateLimitResult.allow(remaining, Algorithm.TOKEN_BUCKET.name());
                    } else {
                        long retryAfter = calculateRetryAfterSeconds(refillRate);
                        log.debug("TokenBucket DENY → key={}, retryAfter={}s", redisKey, retryAfter);
                        return RateLimitResult.deny(retryAfter, Algorithm.TOKEN_BUCKET.name());
                    }
                });
    }

    @Override
    public Algorithm getAlgorithm() {
        return Algorithm.TOKEN_BUCKET;
    }

    /**
     * Redis key format: rl:tb:{clientKey}:{sanitizedPath}
     * Example: rl:tb:mobile-app::api:products
     */
    private String buildRedisKey(String clientKey, String apiPath) {
        String sanitizedPath = apiPath.replace("/", ":").replaceAll("^:", "");
        return "rl:tb:" + clientKey + ":" + sanitizedPath;
    }

    /**
     * How many seconds until at least 1 token is available.
     * With refillRate R tokens/sec → 1 token takes 1/R seconds.
     */
    private long calculateRetryAfterSeconds(double refillRate) {
        if (refillRate <= 0) return 60L;
        return Math.max(1L, Math.round(Math.ceil(1.0 / refillRate)));
    }
}
