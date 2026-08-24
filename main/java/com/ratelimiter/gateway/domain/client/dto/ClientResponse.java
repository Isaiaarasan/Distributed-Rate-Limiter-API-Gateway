package com.ratelimiter.gateway.domain.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ratelimiter.gateway.common.enums.ClientStatus;

import java.time.LocalDateTime;

/**
 * Response DTO for client data.
 *
 * SECURITY NOTE:
 *   The `apiKey` field is only populated on CREATE (first-time reveal).
 *   All subsequent GET requests return null for this field.
 *   The `apiKeyPrefix` field is always safe to return (first 24 chars).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponse(
        Long id,
        String clientKey,
        String clientName,
        String description,
        ClientStatus status,

        /**
         * Raw API key — ONLY populated at creation time.
         * Never stored in plaintext; never returned again after that.
         */
        String apiKey,

        /** Safe prefix of the API key for admin display (e.g., "rl_mobile-app_a1b2c3..."). */
        String apiKeyPrefix,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
