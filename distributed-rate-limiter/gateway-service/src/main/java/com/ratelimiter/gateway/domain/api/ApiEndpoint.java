package com.ratelimiter.gateway.domain.api;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "api_endpoints",
        indexes = {
                @Index(name = "idx_api_path_method", columnList = "path, http_method"),
                @Index(name = "idx_api_status",      columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

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
