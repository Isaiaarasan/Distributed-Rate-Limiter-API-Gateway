package com.ratelimiter.gateway.statistics;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable log entry for every request that passes through the gateway.
 *
 * Used for:
 *   - Analytics dashboards
 *   - Identifying abusive clients
 *   - Debugging rate limit decisions
 *   - Measuring gateway latency
 *
 * Performance note:
 *   Logs are written ASYNCHRONOUSLY from the request path via a bounded thread pool.
 *   This ensures request latency is not affected by log write time.
 */
@Entity
@Table(
        name = "request_logs",
        indexes = {
                @Index(name = "idx_log_client",       columnList = "client_id"),
                @Index(name = "idx_log_api",          columnList = "api_id"),
                @Index(name = "idx_log_requested_at", columnList = "requested_at"),
                @Index(name = "idx_log_allowed",      columnList = "allowed"),
                @Index(name = "idx_log_status",       columnList = "http_status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "api_id")
    private Long apiId;

    /** UUID identifying the individual request (for tracing). */
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    /** Client's IP address (IPv4 or IPv6). */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    /** HTTP status returned to client (200, 429, 401, etc.). */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** true = request forwarded to backend; false = rejected with 429. */
    @Column(name = "allowed", nullable = false)
    private Boolean allowed;

    /** Algorithm used for this decision (TOKEN_BUCKET / SLIDING_WINDOW). */
    @Column(name = "algorithm", length = 50)
    private String algorithm;

    /** Tokens / slots remaining after this request. */
    @Column(name = "tokens_remaining")
    private Integer tokensRemaining;

    /** End-to-end processing time in milliseconds. */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();
}
