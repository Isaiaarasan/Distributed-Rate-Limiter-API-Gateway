package com.ratelimiter.gateway.domain.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ratelimiter.gateway.common.enums.Algorithm;
import com.ratelimiter.gateway.common.exception.ResourceNotFoundException;
import com.ratelimiter.gateway.domain.api.ApiEndpoint;
import com.ratelimiter.gateway.domain.api.ApiEndpointRepository;
import com.ratelimiter.gateway.domain.client.Client;
import com.ratelimiter.gateway.domain.client.ClientRepository;
import com.ratelimiter.gateway.domain.policy.dto.CreatePolicyRequest;
import com.ratelimiter.gateway.domain.policy.dto.PolicyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Business logic for rate limit policy management.
 *
 * Caching Strategy:
 *   findActivePolicy(clientId, apiId) is the CRITICAL HOT PATH — every request.
 *   Cache key: "policy:{clientId}:{apiId}" → Optional<RateLimitPolicy>
 *   TTL: 30 seconds (changes propagate within 30s, no restart needed).
 *
 * Live configuration changes:
 *   When a policy is updated via PUT /api/admin/rate-limits/{id},
 *   the cache is invalidated immediately. New requests will use the new policy.
 *   This is the key "dynamic configuration" feature of the project.
 */
@Service
@Slf4j
public class RateLimitPolicyService {

    private final RateLimitPolicyRepository policyRepository;
    private final ClientRepository clientRepository;
    private final ApiEndpointRepository apiEndpointRepository;

    /** Cache: "policy:{clientId}:{apiId}" → Optional<RateLimitPolicy>. TTL 30 seconds. */
    private final Cache<String, Optional<RateLimitPolicy>> policyCache;

    public RateLimitPolicyService(RateLimitPolicyRepository policyRepository,
                                   ClientRepository clientRepository,
                                   ApiEndpointRepository apiEndpointRepository) {
        this.policyRepository       = policyRepository;
        this.clientRepository       = clientRepository;
        this.apiEndpointRepository  = apiEndpointRepository;
        this.policyCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
        log.info("Rate limit policy cache initialized (maxSize=10000, ttl=30s)");
    }

    // ── Hot Path: Called on Every Rate-Limited Request ────────────────────────

    /**
     * Finds the active (highest-priority) rate limit policy for a client+api pair.
     * Returns empty if no policy is configured (request is allowed through).
     */
    public Mono<RateLimitPolicy> findActivePolicy(Long clientId, Long apiId) {
        String cacheKey = "policy:" + clientId + ":" + apiId;

        return Mono.fromCallable(() -> {
                    Optional<RateLimitPolicy> cached = policyCache.getIfPresent(cacheKey);
                    if (cached != null) {
                        return cached;
                    }
                    Optional<RateLimitPolicy> found = policyRepository.findActivePolicyForClientAndApi(clientId, apiId);
                    policyCache.put(cacheKey, found);
                    return found;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    // ── Admin Operations ──────────────────────────────────────────────────────

    @Transactional
    public Mono<PolicyResponse> createPolicy(CreatePolicyRequest request) {
        return Mono.fromCallable(() -> {
                    // Validate referenced entities exist
                    Client client = clientRepository.findById(request.clientId())
                            .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId()));

                    ApiEndpoint api = apiEndpointRepository.findById(request.apiId())
                            .orElseThrow(() -> new ResourceNotFoundException("ApiEndpoint", request.apiId()));

                    if (policyRepository.existsByClientIdAndApiEndpointId(request.clientId(), request.apiId())) {
                        throw new IllegalStateException(
                                "A policy for client [" + client.getClientKey() + "] and API [" +
                                api.getPath() + "] already exists. Update the existing one instead.");
                    }

                    // Validate algorithm-specific parameters
                    validateParameters(request);

                    RateLimitPolicy policy = RateLimitPolicy.builder()
                            .client(client)
                            .apiEndpoint(api)
                            .algorithm(request.algorithm())
                            .bucketCapacity(request.bucketCapacity())
                            .refillRate(request.refillRate())
                            .maxRequests(request.maxRequests())
                            .windowSeconds(request.windowSeconds())
                            .enabled(request.enabled() != null ? request.enabled() : true)
                            .priority(request.priority() != null ? request.priority() : 0)
                            .build();

                    RateLimitPolicy saved = policyRepository.save(policy);
                    log.info("Created rate limit policy: id={}, client={}, api={}, algo={}",
                            saved.getId(), client.getClientKey(), api.getPath(), saved.getAlgorithm());
                    return toResponse(saved);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<PolicyResponse>> findAll() {
        return Mono.fromCallable(() ->
                        policyRepository.findAll().stream()
                                .map(RateLimitPolicyService::toResponse)
                                .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PolicyResponse> findById(Long id) {
        return Mono.fromCallable(() ->
                        policyRepository.findById(id)
                                .map(RateLimitPolicyService::toResponse)
                                .orElseThrow(() -> new ResourceNotFoundException("RateLimitPolicy", id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<PolicyResponse> updatePolicy(Long id, CreatePolicyRequest request) {
        return Mono.fromCallable(() -> {
                    RateLimitPolicy policy = policyRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("RateLimitPolicy", id));

                    if (request.algorithm() != null)      policy.setAlgorithm(request.algorithm());
                    if (request.bucketCapacity() != null) policy.setBucketCapacity(request.bucketCapacity());
                    if (request.refillRate() != null)     policy.setRefillRate(request.refillRate());
                    if (request.maxRequests() != null)    policy.setMaxRequests(request.maxRequests());
                    if (request.windowSeconds() != null)  policy.setWindowSeconds(request.windowSeconds());
                    if (request.enabled() != null)        policy.setEnabled(request.enabled());
                    if (request.priority() != null)       policy.setPriority(request.priority());

                    RateLimitPolicy saved = policyRepository.save(policy);

                    // Invalidate cache for this client+api pair → changes take effect on next request
                    String cacheKey = "policy:" + saved.getClient().getId() + ":" + saved.getApiEndpoint().getId();
                    policyCache.invalidate(cacheKey);
                    log.info("Updated rate limit policy: id={} (cache invalidated)", saved.getId());

                    return toResponse(saved);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> deletePolicy(Long id) {
        return Mono.fromRunnable(() -> {
                    RateLimitPolicy policy = policyRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("RateLimitPolicy", id));
                    String cacheKey = "policy:" + policy.getClient().getId() + ":" + policy.getApiEndpoint().getId();
                    policyRepository.deleteById(id);
                    policyCache.invalidate(cacheKey);
                    log.info("Deleted rate limit policy: id={}", id);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateParameters(CreatePolicyRequest request) {
        if (request.algorithm() == Algorithm.TOKEN_BUCKET) {
            if (request.bucketCapacity() == null || request.refillRate() == null) {
                throw new IllegalArgumentException(
                        "TOKEN_BUCKET algorithm requires bucketCapacity and refillRate.");
            }
        } else if (request.algorithm() == Algorithm.SLIDING_WINDOW) {
            if (request.maxRequests() == null || request.windowSeconds() == null) {
                throw new IllegalArgumentException(
                        "SLIDING_WINDOW algorithm requires maxRequests and windowSeconds.");
            }
        }
    }

    public static PolicyResponse toResponse(RateLimitPolicy p) {
        return new PolicyResponse(
                p.getId(),
                p.getClient().getId(),
                p.getClient().getClientKey(),
                p.getClient().getClientName(),
                p.getApiEndpoint().getId(),
                p.getApiEndpoint().getPath(),
                p.getApiEndpoint().getHttpMethod(),
                p.getAlgorithm(),
                p.getBucketCapacity(),
                p.getRefillRate(),
                p.getMaxRequests(),
                p.getWindowSeconds(),
                p.getEnabled(),
                p.getPriority(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
