package com.ratelimiter.gateway.ratelimiter;

import com.ratelimiter.gateway.common.enums.Algorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RateLimiterFactoryTest {

    @Test
    @DisplayName("RateLimiterFactory should return correct strategy for algorithm")
    void testGetRateLimiter() {
        RateLimiter tokenBucketLimiter = Mockito.mock(RateLimiter.class);
        when(tokenBucketLimiter.getAlgorithm()).thenReturn(Algorithm.TOKEN_BUCKET);

        RateLimiter slidingWindowLimiter = Mockito.mock(RateLimiter.class);
        when(slidingWindowLimiter.getAlgorithm()).thenReturn(Algorithm.SLIDING_WINDOW);

        RateLimiterFactory factory = new RateLimiterFactory(List.of(tokenBucketLimiter, slidingWindowLimiter));

        assertEquals(tokenBucketLimiter, factory.getRateLimiter(Algorithm.TOKEN_BUCKET));
        assertEquals(slidingWindowLimiter, factory.getRateLimiter(Algorithm.SLIDING_WINDOW));
    }
}
