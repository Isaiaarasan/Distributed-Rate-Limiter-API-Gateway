package com.ratelimiter.gateway.ratelimiter;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicy;
import reactor.core.publisher.Mono;

/**
 * Strategy interface for rate limiting algorithms.
 * Each implementation is a Spring @Component auto-registered in RateLimiterFactory.
 */
public interface RateLimiter {

    Mono<RateLimitResult> isAllowed(String clientKey, String apiPath, RateLimitPolicy policy);

    Algorithm getAlgorithm();
}
