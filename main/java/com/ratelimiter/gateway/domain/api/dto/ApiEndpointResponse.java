package com.ratelimiter.gateway.domain.api.dto;

import java.time.LocalDateTime;

/** Response DTO for API endpoint data. */
public record ApiEndpointResponse(
        Long id,
        String name,
        String path,
        String httpMethod,
        String serviceName,
        String targetUrl,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
