package com.ratelimiter.gateway.ratelimiter.slidingwindow;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicy;
import com.ratelimiter.gateway.ratelimiter.RateLimiter;
import com.ratelimiter.gateway.ratelimiter.RateLimitResult;
import com.ratelimiter.gateway.ratelimiter.redis.LuaScriptExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Sliding Window Rate Limiter
 * ────────────────────────────
 * Algorithm:
 *   - Tracks the timestamps of all requests within a rolling time window
 *   - Window slides with current time (not fixed buckets)
 *   - If requests in window < maxRequests → ALLOW (add timestamp)
 *   - If requests in window ≥ maxRequests → DENY
 *
 * Redis State (Sorted Set / ZSET):
 *   Key:    rl:sw:{clientKey}:{apiPath}
 *   Member: {requestId}:{timestamp}
 *   Score:  timestamp in milliseconds
 *
 * Operations (all in the Lua script):
 *   1. ZREMRANGEBYSCORE key 0 (now - windowMs)  → remove expired entries
 *   2. ZCARD key                                → count current requests
 *   3. If count < maxRequests: ZADD key now requestId
 *   4. EXPIRE key windowSeconds + 1
 *
 * All of the above is a single atomic Lua script → no race condition.
 *
 * Strengths:
 *   ✅ Precise rate limiting (no boundary exploitation)
 *   ✅ No fixed window artifacts
 *   ✅ Self-cleaning (expired entries removed on each request)
 *
 * Weaknesses:
 *   ⚠️ Higher Redis memory usage (stores one ZSET entry per request)
 *   ⚠️ Slightly more complex Lua logic than Token Bucket
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlidingWindowRateLimiter implements RateLimiter {

    private final LuaScriptExecutor luaScriptExecutor;

    @Override
    public Mono<RateLimitResult> isAllowed(String clientKey, String apiPath, RateLimitPolicy policy) {
        String redisKey     = buildRedisKey(clientKey, apiPath);
        long   maxRequests  = policy.getMaxRequests();
        long   windowSeconds = policy.getWindowSeconds();
        long   now          = System.currentTimeMillis();
        String requestId    = generateRequestId();

        log.debug("SlidingWindow check → key={}, maxRequests={}, window={}s",
                redisKey, maxRequests, windowSeconds);

        return luaScriptExecutor.executeSlidingWindow(redisKey, maxRequests, windowSeconds, now, requestId)
                .map(result -> {
                    boolean allowed   = result[0] == 1L;
                    long    remaining = result[1];

                    if (allowed) {
                        log.debug("SlidingWindow ALLOW → key={}, remaining={}", redisKey, remaining);
                        return RateLimitResult.allow(remaining, Algorithm.SLIDING_WINDOW.name());
                    } else {
                        // Client must wait for the oldest request in the window to expire
                        log.debug("SlidingWindow DENY → key={}", redisKey);
                        return RateLimitResult.deny(windowSeconds, Algorithm.SLIDING_WINDOW.name());
                    }
                });
    }

    @Override
    public Algorithm getAlgorithm() {
        return Algorithm.SLIDING_WINDOW;
    }

    /**
     * Redis key format: rl:sw:{clientKey}:{sanitizedPath}
     * Example: rl:sw:partner-api::api:orders
     */
    private String buildRedisKey(String clientKey, String apiPath) {
        String sanitizedPath = apiPath.replace("/", ":").replaceAll("^:", "");
        return "rl:sw:" + clientKey + ":" + sanitizedPath;
    }

    /**
     * Short unique ID used as the ZSET member.
     * 16 hex chars is sufficient and keeps Redis memory low.
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
