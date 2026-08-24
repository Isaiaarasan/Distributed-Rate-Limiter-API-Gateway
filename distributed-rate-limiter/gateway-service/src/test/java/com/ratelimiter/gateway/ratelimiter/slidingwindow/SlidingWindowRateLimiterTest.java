package com.ratelimiter.gateway.ratelimiter.slidingwindow;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicy;
import com.ratelimiter.gateway.ratelimiter.RateLimitResult;
import com.ratelimiter.gateway.ratelimiter.redis.LuaScriptExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimiterTest {

    @Mock
    private LuaScriptExecutor luaScriptExecutor;

    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new SlidingWindowRateLimiter(luaScriptExecutor);
    }

    @Test
    @DisplayName("isAllowed should return allowed=true when under window max requests")
    void testIsAllowed_UnderLimit() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .algorithm(Algorithm.SLIDING_WINDOW)
                .maxRequests(50)
                .windowSeconds(60)
                .build();

        // Mock Lua script returning allowed = 1, remaining = 49
        when(luaScriptExecutor.executeSlidingWindow(anyString(), eq(50L), eq(60L), anyLong(), anyString()))
                .thenReturn(Mono.just(new long[]{1L, 49L}));

        Mono<RateLimitResult> resultMono = rateLimiter.isAllowed("client-1", "/api/products", policy);

        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    assertEquals(true, result.allowed());
                    assertEquals(49L, result.remaining());
                    assertEquals(0L, result.retryAfterSeconds());
                    assertEquals("SLIDING_WINDOW", result.algorithm());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("isAllowed should return allowed=false when window limit is reached")
    void testIsAllowed_LimitReached() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .algorithm(Algorithm.SLIDING_WINDOW)
                .maxRequests(50)
                .windowSeconds(60)
                .build();

        // Mock Lua script returning allowed = 0, remaining = 0
        when(luaScriptExecutor.executeSlidingWindow(anyString(), eq(50L), eq(60L), anyLong(), anyString()))
                .thenReturn(Mono.just(new long[]{0L, 0L}));

        Mono<RateLimitResult> resultMono = rateLimiter.isAllowed("client-1", "/api/products", policy);

        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    assertEquals(false, result.allowed());
                    assertEquals(0L, result.remaining());
                    assertEquals(60L, result.retryAfterSeconds());
                    assertEquals("SLIDING_WINDOW", result.algorithm());
                })
                .verifyComplete();
    }
}
