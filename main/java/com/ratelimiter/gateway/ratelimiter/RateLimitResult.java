package com.ratelimiter.gateway.ratelimiter;

/**
 * Immutable result from a rate limiter check.
 *
 * Fields:
 *   allowed          → true = request can proceed; false = reject with 429
 *   remaining        → tokens/slots remaining after this request
 *   retryAfterSeconds → how many seconds until the client can retry (only relevant when denied)
 *   algorithm        → which algorithm produced this result (for response headers)
 */
public record RateLimitResult(
        boolean allowed,
        long remaining,
        long retryAfterSeconds,
        String algorithm
) {

    /**
     * Factory for an allowed result.
     *
     * @param remaining  tokens or request slots remaining after consuming this one
     * @param algorithm  name of the algorithm that made this decision
     */
    public static RateLimitResult allow(long remaining, String algorithm) {
        return new RateLimitResult(true, remaining, 0L, algorithm);
    }

    /**
     * Factory for a denied result.
     *
     * @param retryAfterSeconds seconds the client should wait before retrying
     * @param algorithm         name of the algorithm that made this decision
     */
    public static RateLimitResult deny(long retryAfterSeconds, String algorithm) {
        return new RateLimitResult(false, 0L, retryAfterSeconds, algorithm);
    }
}
