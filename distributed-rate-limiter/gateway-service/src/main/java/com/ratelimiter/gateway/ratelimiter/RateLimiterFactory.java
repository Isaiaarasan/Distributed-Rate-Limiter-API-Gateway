package com.ratelimiter.gateway.ratelimiter;

import com.ratelimiter.gateway.common.enums.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that maps Algorithm enums to their RateLimiter implementations.
 * Uses Strategy Pattern + Spring dependency injection for auto-discovery.
 */
@Component
@Slf4j
public class RateLimiterFactory {

    private final Map<Algorithm, RateLimiter> rateLimiters;

    public RateLimiterFactory(List<RateLimiter> rateLimiterList) {
        this.rateLimiters = rateLimiterList.stream()
                .collect(Collectors.toMap(RateLimiter::getAlgorithm, rl -> rl));
        log.info("Registered {} rate limiter algorithm(s): {}", rateLimiters.size(), rateLimiters.keySet());
    }

    public RateLimiter getRateLimiter(Algorithm algorithm) {
        RateLimiter rateLimiter = rateLimiters.get(algorithm);
        if (rateLimiter == null) {
            throw new IllegalArgumentException(
                    "No rate limiter registered for algorithm: " + algorithm +
                    ". Available: " + rateLimiters.keySet());
        }
        return rateLimiter;
    }
}
