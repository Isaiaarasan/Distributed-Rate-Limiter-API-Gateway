package com.ratelimiter.gateway.statistics;

import com.ratelimiter.gateway.common.dto.ApiResponse;
import com.ratelimiter.gateway.statistics.dto.StatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Admin REST API for request analytics and statistics.
 *
 * Base path: /api/admin/statistics
 */
@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "View request analytics and rate limit statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping
    @Operation(
        summary     = "Global statistics",
        description = "Returns aggregate statistics: total requests, allowed, denied, " +
                      "success rate, avg latency, top clients, top API paths (last 24h)."
    )
    public Mono<ResponseEntity<ApiResponse<StatisticsResponse>>> getGlobalStatistics() {
        return statisticsService.getGlobalStatistics()
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)));
    }

    @GetMapping("/clients/{clientId}")
    @Operation(summary = "Statistics for a specific client")
    public Mono<ResponseEntity<ApiResponse<StatisticsResponse>>> getClientStatistics(
            @PathVariable Long clientId) {
        return statisticsService.getClientStatistics(clientId)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)));
    }

    @GetMapping("/apis/{apiId}")
    @Operation(summary = "Statistics for a specific API endpoint")
    public Mono<ResponseEntity<ApiResponse<StatisticsResponse>>> getApiStatistics(
            @PathVariable Long apiId) {
        return statisticsService.getApiStatistics(apiId)
                .map(stats -> ResponseEntity.ok(ApiResponse.success(stats)));
    }
}
