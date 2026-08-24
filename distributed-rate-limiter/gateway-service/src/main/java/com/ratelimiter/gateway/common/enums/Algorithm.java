package com.ratelimiter.gateway.common.enums;

/**
 * Rate limiting algorithm selection.
 *
 * TOKEN_BUCKET:
 *   - A bucket holds up to `capacity` tokens
 *   - Tokens are added at `refillRate` tokens/second
 *   - Each request consumes 1 token
 *   - Advantage: allows controlled bursting up to capacity
 *   - State stored in Redis HASH: { tokens, lastRefillTimestamp }
 *
 * SLIDING_WINDOW:
 *   - Tracks exact timestamps of requests within a rolling window
 *   - Rejects requests when count >= maxRequests within windowSeconds
 *   - Advantage: precise, no fixed-window boundary exploitation
 *   - State stored in Redis ZSET: { requestId → timestamp }
 */
public enum Algorithm {

    TOKEN_BUCKET("Token Bucket — controlled burst traffic with gradual refill"),
    SLIDING_WINDOW("Sliding Window — precise request counting over a rolling time window");

    private final String description;

    Algorithm(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
