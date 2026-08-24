package com.ratelimiter.gateway.domain.policy;

import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.domain.api.ApiEndpoint;
import com.ratelimiter.gateway.domain.client.Client;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "bucket_capacity")
    private Integer bucketCapacity;

    @Column(name = "refill_rate")
    private Double refillRate;

    @Column(name = "max_requests")
    private Integer maxRequests;

    @Column(name = "window_seconds")
    private Integer windowSeconds;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

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
