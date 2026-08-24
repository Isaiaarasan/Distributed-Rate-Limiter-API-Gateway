package com.ratelimiter.gateway.domain.client.dto;

import com.ratelimiter.gateway.common.enums.ClientStatus;

public record UpdateClientRequest(
        String clientName,
        String description,
        ClientStatus status
) {}
