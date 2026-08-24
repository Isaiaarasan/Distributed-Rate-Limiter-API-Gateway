package com.ratelimiter.gateway.domain.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ratelimiter.gateway.common.enums.ClientStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponse(
        Long id,
        String clientKey,
        String clientName,
        String description,
        ClientStatus status,
        String apiKey,
        String apiKeyPrefix,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
