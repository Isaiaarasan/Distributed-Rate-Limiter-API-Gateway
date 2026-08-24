package com.ratelimiter.gateway.domain.policy.dto;

import com.ratelimiter.gateway.common.enums.Algorithm;

import java.time.LocalDateTime;

/** Response DTO for rate limit policy data. */
public record PolicyResponse(
        Long id,

        // Client info
        Long clientId,
        String clientKey,
        String clientName,

        // API info
        Long apiId,
        String apiPath,
        String apiMethod,

        // Algorithm + parameters
        Algorithm algorithm,
        Integer bucketCapacity,
        Double refillRate,
        Integer maxRequests,
        Integer windowSeconds,

        // Control
        Boolean enabled,
        Integer priority,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
