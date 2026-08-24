package com.ratelimiter.gateway.domain.policy.dto;

import com.ratelimiter.gateway.common.enums.Algorithm;

import java.time.LocalDateTime;

public record PolicyResponse(
        Long id,
        Long clientId,
        String clientKey,
        String clientName,
        Long apiId,
        String apiPath,
        String apiMethod,
        Algorithm algorithm,
        Integer bucketCapacity,
        Double refillRate,
        Integer maxRequests,
        Integer windowSeconds,
        Boolean enabled,
        Integer priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
