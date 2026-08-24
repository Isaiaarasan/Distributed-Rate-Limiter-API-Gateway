package com.ratelimiter.gateway.domain.client.dto;

import com.ratelimiter.gateway.common.enums.ClientStatus;

/**
 * Request body for updating an existing client.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateClientRequest(
        String clientName,
        String description,
        ClientStatus status
) {}
