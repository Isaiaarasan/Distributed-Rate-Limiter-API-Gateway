package com.ratelimiter.gateway.ratelimiter;

import com.ratelimiter.gateway.common.enums.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory that maps Algorithm enums to their RateLimiter implementations.
 *
 * Uses the Strategy Pattern + Spring's dependency injection:
 *   - Spring collects all RateLimiter implementations at startup
 *   - The factory builds a Map<Algorithm, RateLimiter>
 *   - The gateway filter calls getRateLimiter(policy.getAlgorithm())
 *
 * Adding a new algorithm only requires:
 *   1. Implementing the RateLimiter interface
 *   2. Annotating with @Component
 *   3. No changes to this factory or the filter
 */
@Component
@Slf4j
public class RateLimiterFactory {

    private final Map<Algorithm, RateLimiter> rateLimiters;

    /**
     * Spring injects all RateLimiter beans (TokenBucket, SlidingWindow, etc.)
     * This constructor auto-registers them in the factory map.
     */
    public RateLimiterFactory(List<RateLimiter> rateLimiterList) {
        this.rateLimiters = rateLimiterList.stream()
                .collect(Collectors.toMap(RateLimiter::getAlgorithm, rl -> rl));
        log.info("Registered {} rate limiter algorithm(s): {}", rateLimiters.size(), rateLimiters.keySet());
    }

    /**
     * Returns the appropriate rate limiter for the given algorithm.
     *
     * @param algorithm  the algorithm specified in the rate limit policy
     * @throws IllegalArgumentException if no implementation is registered for this algorithm
     */
    public RateLimiter getRateLimiter(Algorithm algorithm) {
        RateLimiter rateLimiter = rateLimiters.get(algorithm);
        if (rateLimiter == null) {
            throw new IllegalArgumentException(
                    "No rate limiter implementation registered for algorithm: " + algorithm +
                    ". Available: " + rateLimiters.keySet());
        }
        return rateLimiter;
    }
}
