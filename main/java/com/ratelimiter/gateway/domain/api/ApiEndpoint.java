package com.ratelimiter.gateway.domain.api;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a protected API endpoint that can be rate-limited.
 *
 * Examples:
 *   GET  /api/products → routes to product-service
 *   POST /api/orders   → routes to order-service
 *   GET  /api/users    → routes to user-service
 *
 * Path matching strategy:
 *   - Exact match: "/api/products" matches "/api/products"
 *   - Prefix match: "/api/products" matches "/api/products/123" (startsWith)
 *   - httpMethod = "ANY" matches any HTTP method
 */
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

    /** Human-readable name (e.g., "Get Products", "Create Order"). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** URL path prefix to match against incoming requests (e.g., "/api/products"). */
    @Column(name = "path", nullable = false, length = 500)
    private String path;

    /** HTTP method: GET, POST, PUT, DELETE, PATCH, ANY. */
    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    /** Logical service name (e.g., "product-service"). For documentation purposes. */
    @Column(name = "service_name", length = 255)
    private String serviceName;

    /** Target URL of the downstream service. Configured in gateway routes (application.yml). */
    @Column(name = "target_url", length = 500)
    private String targetUrl;

    /** ACTIVE or INACTIVE. Inactive endpoints are not rate-limited (pass-through). */
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
