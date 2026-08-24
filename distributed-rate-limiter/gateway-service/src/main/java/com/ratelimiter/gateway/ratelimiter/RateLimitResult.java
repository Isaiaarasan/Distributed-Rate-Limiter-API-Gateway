package com.ratelimiter.gateway.ratelimiter;

/**
 * Immutable result from a rate limiter check.
 */
public record RateLimitResult(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        String algorithm
) {
    public static RateLimitResult allow(long remaining, String algorithm) {
        return new RateLimitResult(true, remaining, 0L, algorithm);
    }

    public static RateLimitResult deny(long retryAfterSeconds, String algorithm) {
        return new RateLimitResult(false, 0L, retryAfterSeconds, algorithm);
    }
}
