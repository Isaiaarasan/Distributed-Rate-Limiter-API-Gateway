package com.ratelimiter.gateway.statistics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.Map;

/**
 * Response DTO for the statistics dashboard.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatisticsResponse(
        long totalRequests,
        long allowedRequests,
        long deniedRequests,
        double successRate,
        double avgResponseTimeMs,
        long requestsLastHour,
        Map<String, Long> topClients,
        Map<String, Long> topApiPaths
) {}
