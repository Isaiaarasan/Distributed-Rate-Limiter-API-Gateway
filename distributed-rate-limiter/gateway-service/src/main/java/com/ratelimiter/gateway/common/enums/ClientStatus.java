package com.ratelimiter.gateway.common.enums;

/**
 * Lifecycle status for API clients.
 *
 * ACTIVE    - Client can make requests normally
 * INACTIVE  - Client has been disabled by admin (no requests allowed)
 * SUSPENDED - Client violated terms or billing issues (returns 403)
 */
public enum ClientStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
}
