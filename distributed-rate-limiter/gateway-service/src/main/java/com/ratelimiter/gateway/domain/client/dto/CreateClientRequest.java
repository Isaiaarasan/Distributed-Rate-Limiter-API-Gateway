package com.ratelimiter.gateway.domain.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateClientRequest(
        @NotBlank(message = "clientKey is required")
        @Size(min = 2, max = 100, message = "clientKey must be between 2 and 100 characters")
        String clientKey,

        @NotBlank(message = "clientName is required")
        @Size(max = 255, message = "clientName must not exceed 255 characters")
        String clientName,

        @Size(max = 1000, message = "description must not exceed 1000 characters")
        String description
) {}
