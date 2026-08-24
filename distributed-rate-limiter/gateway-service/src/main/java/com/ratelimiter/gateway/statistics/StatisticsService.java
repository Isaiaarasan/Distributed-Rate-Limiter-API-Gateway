package com.ratelimiter.gateway.statistics;

import com.ratelimiter.gateway.statistics.dto.StatisticsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statistics service for request analytics.
 *
 * Logging design:
 *   logRequest() is annotated with @Async so it does NOT block the request path.
 *   The gateway filter calls it fire-and-forget style.
 *   If logging fails (DB down), the request itself was already allowed/denied
 *   and the user is unaffected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {

    private final RequestLogRepository requestLogRepository;

    // ── Request Logging (Async - Non-blocking from request path) ─────────────

    /**
     * Asynchronously saves a request log entry to MySQL.
     * Called from the gateway filter after the rate limit decision.
     *
     * @Async ensures this runs on a separate thread from Spring's async executor,
     * not on the Netty event-loop thread.
     */
    @Async
    public void logRequest(RequestLog requestLog) {
        try {
            requestLogRepository.save(requestLog);
        } catch (Exception e) {
            log.warn("Failed to save request log (non-critical): {}", e.getMessage());
        }
    }

    // ── Statistics Queries ───────────────────────────────────────────────────

    /** Global statistics across all clients and APIs. */
    public Mono<StatisticsResponse> getGlobalStatistics() {
        return Mono.fromCallable(() -> {
                    long total   = requestLogRepository.count();
                    long allowed = requestLogRepository.countByAllowed(true);
                    long denied  = requestLogRepository.countByAllowed(false);
                    double rate  = total == 0 ? 100.0 : (allowed * 100.0 / total);

                    LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
                    long lastHour = requestLogRepository.countSince(oneHourAgo);
                    Double avgLatency = requestLogRepository.avgResponseTimeSince(
                            LocalDateTime.now().minusHours(24));

                    List<Object[]> topClients = requestLogRepository.findTopClientsSince(
                            LocalDateTime.now().minusHours(24), PageRequest.of(0, 5));
                    List<Object[]> topApis = requestLogRepository.findTopApiPathsSince(
                            LocalDateTime.now().minusHours(24), PageRequest.of(0, 5));

                    return StatisticsResponse.builder()
                            .totalRequests(total)
                            .allowedRequests(allowed)
                            .deniedRequests(denied)
                            .successRate(Math.round(rate * 100.0) / 100.0)
                            .avgResponseTimeMs(avgLatency != null ? Math.round(avgLatency * 100.0) / 100.0 : 0.0)
                            .requestsLastHour(lastHour)
                            .topClients(toMap(topClients))
                            .topApiPaths(toMap(topApis))
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Statistics for a specific client. */
    public Mono<StatisticsResponse> getClientStatistics(Long clientId) {
        return Mono.fromCallable(() -> {
                    long total   = requestLogRepository.countByClientId(clientId);
                    long allowed = requestLogRepository.countByClientIdAndAllowed(clientId, true);
                    long denied  = requestLogRepository.countByClientIdAndAllowed(clientId, false);
                    double rate  = total == 0 ? 100.0 : (allowed * 100.0 / total);

                    return StatisticsResponse.builder()
                            .totalRequests(total)
                            .allowedRequests(allowed)
                            .deniedRequests(denied)
                            .successRate(Math.round(rate * 100.0) / 100.0)
                            .avgResponseTimeMs(0.0)
                            .requestsLastHour(0L)
                            .topClients(Map.of())
                            .topApiPaths(Map.of())
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** Statistics for a specific API endpoint. */
    public Mono<StatisticsResponse> getApiStatistics(Long apiId) {
        return Mono.fromCallable(() -> {
                    long total   = requestLogRepository.countByApiId(apiId);
                    long allowed = requestLogRepository.countByApiIdAndAllowed(apiId, true);
                    long denied  = requestLogRepository.countByApiIdAndAllowed(apiId, false);
                    double rate  = total == 0 ? 100.0 : (allowed * 100.0 / total);

                    return StatisticsResponse.builder()
                            .totalRequests(total)
                            .allowedRequests(allowed)
                            .deniedRequests(denied)
                            .successRate(Math.round(rate * 100.0) / 100.0)
                            .avgResponseTimeMs(0.0)
                            .requestsLastHour(0L)
                            .topClients(Map.of())
                            .topApiPaths(Map.of())
                            .build();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "unknown";
            Long   val = row[1] instanceof Number n ? n.longValue() : 0L;
            result.put(key, val);
        }
        return result;
    }
}
