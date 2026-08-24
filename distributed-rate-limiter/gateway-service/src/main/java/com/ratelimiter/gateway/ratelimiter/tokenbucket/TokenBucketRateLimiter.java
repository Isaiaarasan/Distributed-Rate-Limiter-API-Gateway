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

        return luaScriptExecutor.executeTokenBucket(redisKey, capacity, refillRate, now)
                .map(result -> {
                    boolean allowed   = result[0] == 1L;
                    long    remaining = result[1];

                    if (allowed) {
                        return RateLimitResult.allow(remaining, Algorithm.TOKEN_BUCKET.name());
                    } else {
                        long retryAfter = calculateRetryAfterSeconds(refillRate);
                        return RateLimitResult.deny(retryAfter, Algorithm.TOKEN_BUCKET.name());
                    }
                });
    }

    @Override
    public Algorithm getAlgorithm() {
        return Algorithm.TOKEN_BUCKET;
    }

    private String buildRedisKey(String clientKey, String apiPath) {
        String sanitizedPath = apiPath.replace("/", ":").replaceAll("^:", "");
        return "rl:tb:" + clientKey + ":" + sanitizedPath;
    }

    private long calculateRetryAfterSeconds(double refillRate) {
        if (refillRate <= 0) return 60L;
        return Math.max(1L, Math.round(Math.ceil(1.0 / refillRate)));
    }
}
