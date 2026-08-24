package com.ratelimiter.gateway.domain.policy;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.api.ApiEndpoint;
import com.ratelimiter.gateway.domain.client.Client;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The heart of the rate limiter: ties a Client to an ApiEndpoint with a specific algorithm and limits.
 *
 * Token Bucket policy example:
 *   Client: mobile-app
 *   API: GET /api/products
 *   Algorithm: TOKEN_BUCKET
 *   bucketCapacity: 100   ← max tokens the bucket can hold
 *   refillRate: 2.0       ← tokens added per second
 *
 * Sliding Window policy example:
 *   Client: partner-api
 *   API: POST /api/orders
 *   Algorithm: SLIDING_WINDOW
 *   maxRequests: 20       ← max requests allowed in the window
 *   windowSeconds: 60     ← rolling window size in seconds
 *
 * The `priority` field allows multiple policies per client (higher priority wins).
 * The `enabled` flag allows instant on/off without deletion.
 */
@Entity
@Table(
        name = "rate_limit_policies",
        indexes = {
                @Index(name = "idx_policy_client",     columnList = "client_id"),
                @Index(name = "idx_policy_api",        columnList = "api_id"),
                @Index(name = "idx_policy_client_api", columnList = "client_id, api_id"),
                @Index(name = "idx_policy_enabled",    columnList = "enabled")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "api_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ApiEndpoint apiEndpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", nullable = false, length = 50)
    private Algorithm algorithm;

    // ── Token Bucket parameters ──────────────────────────────────────────────
    /** Maximum number of tokens the bucket can hold. */
    @Column(name = "bucket_capacity")
    private Integer bucketCapacity;

    /** Number of tokens added per second (can be fractional, e.g., 0.5 = 1 token/2sec). */
    @Column(name = "refill_rate")
    private Double refillRate;

    // ── Sliding Window parameters ────────────────────────────────────────────
    /** Maximum number of requests allowed within the window. */
    @Column(name = "max_requests")
    private Integer maxRequests;

    /** Rolling window duration in seconds. */
    @Column(name = "window_seconds")
    private Integer windowSeconds;

    // ── Control fields ───────────────────────────────────────────────────────
    /** Toggle without deleting the policy. */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** Higher priority wins when multiple policies match the same client+api. */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
