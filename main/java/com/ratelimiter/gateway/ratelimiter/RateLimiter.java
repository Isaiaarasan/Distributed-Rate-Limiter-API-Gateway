package com.ratelimiter.gateway.ratelimiter;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicy;
import reactor.core.publisher.Mono;

/**
 * Strategy interface for rate limiting algorithms.
 *
 * Each implementation (Token Bucket, Sliding Window) is a Spring component
 * that is automatically registered in the RateLimiterFactory.
 *
 * The factory uses the Strategy Pattern:
 *   RateLimiterFactory.getRateLimiter(Algorithm.TOKEN_BUCKET) → TokenBucketRateLimiter
 *   RateLimiterFactory.getRateLimiter(Algorithm.SLIDING_WINDOW) → SlidingWindowRateLimiter
 *
 * All implementations MUST be:
 *   1. Thread-safe (reactive, stateless at the Java level)
 *   2. Atomic (state is managed atomically via Redis Lua scripts)
 *   3. Fail-open (if Redis is down, allow the request and log the error)
 */
public interface RateLimiter {

    /**
     * Determines whether the given client is allowed to access the given API path
     * according to the provided rate limit policy.
     *
     * @param clientKey  unique key for the client (e.g., "mobile-app")
     * @param apiPath    request path (e.g., "/api/products")
     * @param policy     the rate limit policy from MySQL
     * @return           Mono emitting the rate limit decision
     */
    Mono<RateLimitResult> isAllowed(String clientKey, String apiPath, RateLimitPolicy policy);

    /**
     * Returns the algorithm this implementation handles.
     * Used by RateLimiterFactory to auto-register implementations.
     */
    Algorithm getAlgorithm();
}
