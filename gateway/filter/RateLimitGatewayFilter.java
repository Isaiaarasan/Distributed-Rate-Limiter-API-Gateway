package com.ratelimiter.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ratelimiter.gateway.common.enums.ClientStatus;
import com.ratelimiter.gateway.domain.api.ApiEndpointService;
import com.ratelimiter.gateway.domain.client.Client;
import com.ratelimiter.gateway.domain.client.ClientService;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicy;
import com.ratelimiter.gateway.domain.policy.RateLimitPolicyService;
import com.ratelimiter.gateway.ratelimiter.RateLimitResult;
import com.ratelimiter.gateway.ratelimiter.RateLimiterFactory;
import com.ratelimiter.gateway.statistics.RequestLog;
import com.ratelimiter.gateway.statistics.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Rate Limit Gateway Filter — The Core of the System
 * ────────────────────────────────────────────────────
 *
 * This GlobalFilter intercepts EVERY request passing through Spring Cloud Gateway.
 * It is the orchestrator that ties together all layers:
 *
 *   1. Extract API Key from X-API-Key header
 *   2. Look up Client from MySQL (Caffeine cached)
 *   3. Match request path to ApiEndpoint (Caffeine cached)
 *   4. Look up RateLimitPolicy for client+api (Caffeine cached)
 *   5. Select algorithm (TOKEN_BUCKET or SLIDING_WINDOW)
 *   6. Execute Lua script atomically in Redis
 *   7a. ALLOWED → add rate limit headers → forward to backend
 *   7b. DENIED  → return HTTP 429 with Retry-After
 *   8. Log request asynchronously to MySQL
 *
 * Fail-open design:
 *   If Redis is unavailable, LuaScriptExecutor catches the error and returns allowed=true.
 *   Availability is prioritized over strict rate limiting during infrastructure failures.
 *
 * Thread safety:
 *   All JPA calls are wrapped in Schedulers.boundedElastic() by the services.
 *   Redis Lua calls are also wrapped. The filter chain remains reactive/non-blocking.
 */
@Component
@Slf4j
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

    private final ClientService           clientService;
    private final ApiEndpointService      apiEndpointService;
    private final RateLimitPolicyService  policyService;
    private final RateLimiterFactory      rateLimiterFactory;
    private final StatisticsService       statisticsService;
    private final ObjectMapper            objectMapper;

    public RateLimitGatewayFilter(ClientService clientService,
                                   ApiEndpointService apiEndpointService,
                                   RateLimitPolicyService policyService,
                                   RateLimiterFactory rateLimiterFactory,
                                   StatisticsService statisticsService) {
        this.clientService      = clientService;
        this.apiEndpointService = apiEndpointService;
        this.policyService      = policyService;
        this.rateLimiterFactory = rateLimiterFactory;
        this.statisticsService  = statisticsService;
        this.objectMapper       = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Run before all other filters (highest priority). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path   = request.getPath().pathWithinApplication().value();
        String method = Objects.requireNonNull(request.getMethod()).name();

        // ── 1. Skip admin and internal paths ──────────────────────────────────
        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        // ── 2. Extract API Key ────────────────────────────────────────────────
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "API_KEY_MISSING",
                    "X-API-Key header is required. Include your API key with every request.");
        }

        long startTime = System.currentTimeMillis();

        // ── 3. Client lookup (Caffeine cached, MySQL-backed) ──────────────────
        return clientService.findByApiKey(apiKey)
                .switchIfEmpty(Mono.defer(() ->
                        writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                                "The provided API key is invalid or does not exist.").then(Mono.empty())))

                .flatMap(client -> {
                    // ── 4. Check client status ─────────────────────────────
                    if (client.getStatus() == ClientStatus.INACTIVE) {
                        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "CLIENT_INACTIVE",
                                "Your client account has been deactivated. Contact admin.");
                    }
                    if (client.getStatus() == ClientStatus.SUSPENDED) {
                        return writeErrorResponse(exchange, HttpStatus.FORBIDDEN, "CLIENT_SUSPENDED",
                                "Your client account is suspended. Contact admin.");
                    }

                    // ── 5. Match API endpoint (Caffeine cached, MySQL-backed) ──────────────
                    return apiEndpointService.matchEndpoint(path, method)
                            .flatMap(apiEndpoint ->
                                    // ── 6. Find rate limit policy ─────────────
                                    policyService.findActivePolicy(client.getId(), apiEndpoint.getId())
                                            .flatMap(policy ->
                                                    // ── 7. Execute rate limiter ─────────
                                                    executeRateLimit(exchange, chain, client, policy, request, path, startTime))
                                            .switchIfEmpty(
                                                    // No policy for this client+api → allow through
                                                    forwardRequest(exchange, chain, client, startTime)))
                            .switchIfEmpty(
                                    // No matching API endpoint → allow through (unprotected)
                                    forwardRequest(exchange, chain, client, startTime));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<Void> executeRateLimit(ServerWebExchange exchange,
                                         GatewayFilterChain chain,
                                         Client client,
                                         RateLimitPolicy policy,
                                         ServerHttpRequest request,
                                         String path,
                                         long startTime) {
        return rateLimiterFactory
                .getRateLimiter(policy.getAlgorithm())
                .isAllowed(client.getClientKey(), path, policy)
                .flatMap(result -> {
                    long responseTime = System.currentTimeMillis() - startTime;

                    // Fire-and-forget async logging
                    logRequest(client, policy, request, result, responseTime);

                    if (result.allowed()) {
                        // ── Add rate limit headers to response ─────────────
                        long limit = getLimitFromPolicy(policy);
                        exchange.getResponse().getHeaders().set("X-RateLimit-Limit",     String.valueOf(limit));
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(result.remaining()));
                        exchange.getResponse().getHeaders().set("X-RateLimit-Algorithm", result.algorithm());

                        // Forward request to backend
                        ServerWebExchange mutated = exchange.mutate()
                                .request(request.mutate()
                                        .header("X-Client-Id", client.getClientKey())
                                        .header("X-Request-Id", UUID.randomUUID().toString())
                                        .build())
                                .build();
                        return chain.filter(mutated);
                    } else {
                        // ── Return 429 Too Many Requests ───────────────────
                        exchange.getResponse().getHeaders().set("Retry-After",          String.valueOf(result.retryAfterSeconds()));
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
                        exchange.getResponse().getHeaders().set("X-RateLimit-Algorithm", result.algorithm());

                        log.info("RATE LIMITED: client={}, path={}, algo={}, retryAfter={}s",
                                client.getClientKey(), path, result.algorithm(), result.retryAfterSeconds());

                        return writeRateLimitExceededResponse(exchange, result, client.getClientKey(), path);
                    }
                });
    }

    private Mono<Void> forwardRequest(ServerWebExchange exchange, GatewayFilterChain chain,
                                       Client client, long startTime) {
        ServerWebExchange mutated = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-Client-Id", client.getClientKey())
                        .build())
                .build();
        return chain.filter(mutated);
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/api/admin")     ||
               path.startsWith("/actuator")      ||
               path.startsWith("/swagger-ui")    ||
               path.startsWith("/v3/api-docs")   ||
               path.startsWith("/webjars")       ||
               path.equals("/favicon.ico");
    }

    private long getLimitFromPolicy(RateLimitPolicy policy) {
        return switch (policy.getAlgorithm()) {
            case TOKEN_BUCKET    -> policy.getBucketCapacity();
            case SLIDING_WINDOW  -> policy.getMaxRequests();
        };
    }

    private void logRequest(Client client, RateLimitPolicy policy,
                             ServerHttpRequest request, RateLimitResult result, long responseTimeMs) {
        try {
            String ipAddress = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            RequestLog logEntry = RequestLog.builder()
                    .clientId(client.getId())
                    .apiId(policy.getApiEndpoint().getId())
                    .requestId(UUID.randomUUID().toString())
                    .ipAddress(ipAddress)
                    .httpMethod(Objects.requireNonNull(request.getMethod()).name())
                    .requestPath(request.getPath().value())
                    .httpStatus(result.allowed() ? 200 : 429)
                    .allowed(result.allowed())
                    .algorithm(result.algorithm())
                    .tokensRemaining((int) result.remaining())
                    .responseTimeMs(responseTimeMs)
                    .requestedAt(LocalDateTime.now())
                    .build();

            statisticsService.logRequest(logEntry); // @Async — non-blocking
        } catch (Exception e) {
            log.warn("Failed to create log entry: {}", e.getMessage());
        }
    }

    private Mono<Void> writeRateLimitExceededResponse(ServerWebExchange exchange,
                                                        RateLimitResult result,
                                                        String clientKey,
                                                        String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", "RATE_LIMIT_EXCEEDED");
        body.put("message", "Too many requests. You have exceeded your rate limit.");
        body.put("clientKey", clientKey);
        body.put("path", path);
        body.put("algorithm", result.algorithm());
        body.put("retryAfterSeconds", result.retryAfterSeconds());
        body.put("timestamp", LocalDateTime.now().toString());

        return writeJsonResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, body);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange, HttpStatus status,
                                           String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", errorCode);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        return writeJsonResponse(exchange, status, body);
    }

    private Mono<Void> writeJsonResponse(ServerWebExchange exchange, HttpStatus status, Map<String, Object> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[]     bytes  = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response: {}", e.getMessage());
            return exchange.getResponse().setComplete();
        }
    }
}
