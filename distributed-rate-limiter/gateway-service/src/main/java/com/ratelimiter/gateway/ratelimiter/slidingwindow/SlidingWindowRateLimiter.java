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

        return luaScriptExecutor.executeSlidingWindow(redisKey, maxRequests, windowSeconds, now, requestId)
                .map(result -> {
                    boolean allowed   = result[0] == 1L;
                    long    remaining = result[1];

                    if (allowed) {
                        return RateLimitResult.allow(remaining, Algorithm.SLIDING_WINDOW.name());
                    } else {
                        return RateLimitResult.deny(windowSeconds, Algorithm.SLIDING_WINDOW.name());
                    }
                });
    }

    @Override
    public Algorithm getAlgorithm() {
        return Algorithm.SLIDING_WINDOW;
    }

    private String buildRedisKey(String clientKey, String apiPath) {
        String sanitizedPath = apiPath.replace("/", ":").replaceAll("^:", "");
        return "rl:sw:" + clientKey + ":" + sanitizedPath;
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
