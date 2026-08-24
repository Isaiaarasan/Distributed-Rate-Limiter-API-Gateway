package com.ratelimiter.gateway.statistics;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "allowed", nullable = false)
    private Boolean allowed;

    @Column(name = "algorithm", length = 50)
    private String algorithm;

    @Column(name = "tokens_remaining")
    private Integer tokensRemaining;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();
}
