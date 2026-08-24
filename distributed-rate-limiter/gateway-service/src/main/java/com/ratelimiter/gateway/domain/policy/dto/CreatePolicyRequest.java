package com.ratelimiter.gateway.domain.policy.dto;

import com.ratelimiter.gateway.common.enums.Algorithm;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePolicyRequest(
        @NotNull(message = "clientId is required")
        Long clientId,

        @NotNull(message = "apiId is required")
        Long apiId,

        @NotNull(message = "algorithm is required (TOKEN_BUCKET or SLIDING_WINDOW)")
        Algorithm algorithm,

        @Min(value = 1, message = "bucketCapacity must be at least 1")
        Integer bucketCapacity,

        @Min(value = 0, message = "refillRate must be positive")
        Double refillRate,

        @Min(value = 1, message = "maxRequests must be at least 1")
        Integer maxRequests,

        @Min(value = 1, message = "windowSeconds must be at least 1")
        Integer windowSeconds,

        Boolean enabled,
        Integer priority
) {}
