package com.ratelimiter.gateway.common.exception;

/**
 * Thrown when a client exceeds their configured rate limit.
 * Results in HTTP 429 Too Many Requests with a Retry-After header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;
    private final String clientKey;
    private final String path;

    public RateLimitExceededException(long retryAfterSeconds, String clientKey, String path) {
        super(String.format("Rate limit exceeded for client '%s' on path '%s'. Retry after %d seconds.",
                clientKey, path, retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
        this.clientKey = clientKey;
        this.path = path;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String getClientKey() {
        return clientKey;
    }

    public String getPath() {
        return path;
    }
}
