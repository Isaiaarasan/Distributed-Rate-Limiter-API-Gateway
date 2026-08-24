package com.ratelimiter.gateway.ratelimiter.tokenbucket;

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
class TokenBucketRateLimiterTest {

    @Mock
    private LuaScriptExecutor luaScriptExecutor;

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter(luaScriptExecutor);
    }

    @Test
    @DisplayName("isAllowed should return allowed=true when bucket has tokens")
    void testIsAllowed_TokensAvailable() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .bucketCapacity(10)
                .refillRate(2.0)
                .build();

        // Mock Lua script returning allowed = 1, remaining = 9
        when(luaScriptExecutor.executeTokenBucket(anyString(), eq(10L), eq(2.0), anyLong()))
                .thenReturn(Mono.just(new long[]{1L, 9L}));

        Mono<RateLimitResult> resultMono = rateLimiter.isAllowed("client-1", "/api/products", policy);

        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    assertEquals(true, result.allowed());
                    assertEquals(9L, result.remaining());
                    assertEquals(0L, result.retryAfterSeconds());
                    assertEquals("TOKEN_BUCKET", result.algorithm());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("isAllowed should return allowed=false when bucket is exhausted")
    void testIsAllowed_BucketExhausted() {
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .algorithm(Algorithm.TOKEN_BUCKET)
                .bucketCapacity(10)
                .refillRate(2.0)
                .build();

        // Mock Lua script returning allowed = 0, remaining = 0
        when(luaScriptExecutor.executeTokenBucket(anyString(), eq(10L), eq(2.0), anyLong()))
                .thenReturn(Mono.just(new long[]{0L, 0L}));

        Mono<RateLimitResult> resultMono = rateLimiter.isAllowed("client-1", "/api/products", policy);

        StepVerifier.create(resultMono)
                .assertNext(result -> {
                    assertEquals(false, result.allowed());
                    assertEquals(0L, result.remaining());
                    assertEquals(1L, result.retryAfterSeconds()); // 1/2.0 rounded up is 1s
                    assertEquals("TOKEN_BUCKET", result.algorithm());
                })
                .verifyComplete();
    }
}
