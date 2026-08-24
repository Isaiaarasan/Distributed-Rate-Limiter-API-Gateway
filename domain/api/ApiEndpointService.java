package com.ratelimiter.gateway.domain.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ratelimiter.gateway.common.exception.ResourceNotFoundException;
import com.ratelimiter.gateway.domain.api.dto.ApiEndpointResponse;
import com.ratelimiter.gateway.domain.api.dto.CreateApiEndpointRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Business logic for API endpoint registration and path matching.
 *
 * Caching strategy:
 *   matchEndpoint(path, method) is called on every request.
 *   Cache key: "{path}:{method}" → Optional<ApiEndpoint>
 *   TTL: 30 seconds (allows live updates to propagate quickly).
 */
@Service
@Slf4j
public class ApiEndpointService {

    private final ApiEndpointRepository apiEndpointRepository;

    /** Cache: "{path}:{method}" → Optional<ApiEndpoint>. TTL 30 seconds. */
    private final Cache<String, Optional<ApiEndpoint>> pathMatchCache;

    public ApiEndpointService(ApiEndpointRepository apiEndpointRepository) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.pathMatchCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
        log.info("API endpoint path-match cache initialized (maxSize=1000, ttl=30s)");
    }

    // ── Hot Path: Called on Every Request ────────────────────────────────────

    /**
     * Finds the best matching API endpoint for the given request path and method.
     * Returns empty if no active endpoint matches (request is passed through without rate limiting).
     */
    public Mono<ApiEndpoint> matchEndpoint(String path, String method) {
        String cacheKey = path + ":" + method;

        return Mono.fromCallable(() -> {
                    Optional<ApiEndpoint> cached = pathMatchCache.getIfPresent(cacheKey);
                    if (cached != null) {
                        return cached;
                    }
                    List<ApiEndpoint> matches = apiEndpointRepository.findMatchingEndpoints(path, method);
                    Optional<ApiEndpoint> result = matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
                    pathMatchCache.put(cacheKey, result);
                    return result;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    // ── Admin Operations ──────────────────────────────────────────────────────

    @Transactional
    public Mono<ApiEndpointResponse> createEndpoint(CreateApiEndpointRequest request) {
        return Mono.fromCallable(() -> {
                    if (apiEndpointRepository.existsByPathAndHttpMethod(request.path(), request.httpMethod())) {
                        throw new IllegalStateException(
                                "Endpoint [" + request.httpMethod() + " " + request.path() + "] already exists.");
                    }

                    ApiEndpoint endpoint = ApiEndpoint.builder()
                            .name(request.name())
                            .path(request.path())
                            .httpMethod(request.httpMethod())
                            .serviceName(request.serviceName())
                            .targetUrl(request.targetUrl())
                            .status("ACTIVE")
                            .build();

                    ApiEndpoint saved = apiEndpointRepository.save(endpoint);
                    pathMatchCache.invalidateAll(); // Clear cache after schema change
                    log.info("Registered API endpoint: {} {}", saved.getHttpMethod(), saved.getPath());
                    return toResponse(saved);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<ApiEndpointResponse>> findAll() {
        return Mono.fromCallable(() ->
                        apiEndpointRepository.findAll().stream()
                                .map(ApiEndpointService::toResponse)
                                .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApiEndpoint> findById(Long id) {
        return Mono.fromCallable(() ->
                        apiEndpointRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("ApiEndpoint", id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<ApiEndpointResponse> updateEndpoint(Long id, CreateApiEndpointRequest request) {
        return Mono.fromCallable(() -> {
                    ApiEndpoint endpoint = apiEndpointRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("ApiEndpoint", id));

                    if (request.name() != null)        endpoint.setName(request.name());
                    if (request.path() != null)        endpoint.setPath(request.path());
                    if (request.httpMethod() != null)  endpoint.setHttpMethod(request.httpMethod());
                    if (request.serviceName() != null) endpoint.setServiceName(request.serviceName());
                    if (request.targetUrl() != null)   endpoint.setTargetUrl(request.targetUrl());

                    ApiEndpoint saved = apiEndpointRepository.save(endpoint);
                    pathMatchCache.invalidateAll();
                    log.info("Updated API endpoint: id={}, {} {}", saved.getId(), saved.getHttpMethod(), saved.getPath());
                    return toResponse(saved);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> deleteEndpoint(Long id) {
        return Mono.fromRunnable(() -> {
                    if (!apiEndpointRepository.existsById(id)) {
                        throw new ResourceNotFoundException("ApiEndpoint", id);
                    }
                    apiEndpointRepository.deleteById(id);
                    pathMatchCache.invalidateAll();
                    log.info("Deleted API endpoint: id={}", id);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public static ApiEndpointResponse toResponse(ApiEndpoint e) {
        return new ApiEndpointResponse(
                e.getId(), e.getName(), e.getPath(), e.getHttpMethod(),
                e.getServiceName(), e.getTargetUrl(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
