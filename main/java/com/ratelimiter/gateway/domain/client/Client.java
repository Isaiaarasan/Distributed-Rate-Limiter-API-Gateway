package com.ratelimiter.gateway.domain.client;

import com.ratelimiter.gateway.common.enums.ClientStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents an API consumer (e.g., mobile-app, web-app, partner-api).
 *
 * Security design:
 *   - The raw API key is NEVER stored. Only the SHA-256 hash is persisted.
 *   - api_key_prefix is stored for admin display (safe to expose).
 *   - On each request, X-API-Key → sha256(key) → lookup by api_key_hash.
 */
@Entity
@Table(
        name = "clients",
        indexes = {
                @Index(name = "idx_client_api_key_hash", columnList = "api_key_hash"),
                @Index(name = "idx_client_key",         columnList = "client_key")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique human-readable identifier (e.g., "mobile-app", "partner-x"). */
    @Column(name = "client_key", unique = true, nullable = false, length = 100)
    private String clientKey;

    /** Display name (e.g., "Mobile Application v2"). */
    @Column(name = "client_name", nullable = false, length = 255)
    private String clientName;

    /** SHA-256 hash of the raw API key for secure lookup. Never the plaintext key. */
    @Column(name = "api_key_hash", unique = true, nullable = false, length = 64)
    private String apiKeyHash;

    /** First ~24 characters of the raw key (safe to display in admin UI). */
    @Column(name = "api_key_prefix", length = 30)
    private String apiKeyPrefix;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

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
