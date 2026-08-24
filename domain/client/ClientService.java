package com.ratelimiter.gateway.domain.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ratelimiter.gateway.common.enums.ClientStatus;
import com.ratelimiter.gateway.common.exception.ResourceNotFoundException;
import com.ratelimiter.gateway.common.util.HashUtil;
import com.ratelimiter.gateway.domain.client.dto.ClientResponse;
import com.ratelimiter.gateway.domain.client.dto.CreateClientRequest;
import com.ratelimiter.gateway.domain.client.dto.UpdateClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Business logic for API client management.
 *
 * Caching Strategy:
 *   findByApiKey() is called on EVERY incoming request (the hot path).
 *   It is cached with a 5-minute TTL using Caffeine in-memory cache.
 *   Cache key = sha256(rawApiKey)
 *
 * Why not @Cacheable?
 *   @Cacheable + Mono<T> requires Spring's reactive cache support setup.
 *   Using a manual Caffeine cache is simpler and gives us explicit control
 *   over invalidation on client updates.
 */
@Service
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;

    /** Cache: sha256(apiKey) → Optional<Client>. TTL 5 minutes. */
    private final Cache<String, Optional<Client>> apiKeyCache;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
        this.apiKeyCache = Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();
        log.info("Client API key cache initialized (maxSize=5000, ttl=5min)");
    }

    // ── Hot Path: Called on Every Request ────────────────────────────────────

    /**
     * Looks up a client by their raw API key (X-API-Key header value).
     * Uses Caffeine cache to avoid a MySQL query on every request.
     *
     * @param rawApiKey the API key from the X-API-Key header (plaintext)
     * @return Mono<Client> if found and active, empty Mono if not found
     */
    public Mono<Client> findByApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            return Mono.empty();
        }

        String hashedKey = HashUtil.sha256(rawApiKey);

        return Mono.fromCallable(() -> {
                    Optional<Client> cached = apiKeyCache.getIfPresent(hashedKey);
                    if (cached != null) {
                        log.debug("Cache HIT for API key prefix: {}...", rawApiKey.substring(0, Math.min(10, rawApiKey.length())));
                        return cached;
                    }
                    log.debug("Cache MISS — querying MySQL for API key");
                    Optional<Client> found = clientRepository.findByApiKeyHash(hashedKey);
                    apiKeyCache.put(hashedKey, found);
                    return found;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty);
    }

    // ── Admin Operations ──────────────────────────────────────────────────────

    /**
     * Creates a new client and generates their API key.
     * Returns a pair: (saved Client, raw API key).
     * The raw API key is shown ONCE in the response and never stored.
     */
    @Transactional
    public Mono<ClientWithKey> createClient(CreateClientRequest request) {
        return Mono.fromCallable(() -> {
                    if (clientRepository.existsByClientKey(request.clientKey())) {
                        throw new IllegalStateException(
                                "Client key [" + request.clientKey() + "] already exists.");
                    }

                    String rawApiKey  = HashUtil.generateApiKey(request.clientKey());
                    String apiKeyHash = HashUtil.sha256(rawApiKey);
                    String apiKeyPrefix = HashUtil.extractPrefix(rawApiKey);

                    Client client = Client.builder()
                            .clientKey(request.clientKey())
                            .clientName(request.clientName())
                            .description(request.description())
                            .apiKeyHash(apiKeyHash)
                            .apiKeyPrefix(apiKeyPrefix)
                            .status(ClientStatus.ACTIVE)
                            .build();

                    Client saved = clientRepository.save(client);
                    log.info("Created client: id={}, key={}", saved.getId(), saved.getClientKey());
                    return new ClientWithKey(saved, rawApiKey);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<ClientResponse>> findAll() {
        return Mono.fromCallable(() ->
                        clientRepository.findAll().stream()
                                .map(c -> toResponse(c, null))
                                .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Client> findById(Long id) {
        return Mono.fromCallable(() ->
                        clientRepository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Client", id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<ClientResponse> updateClient(Long id, UpdateClientRequest request) {
        return Mono.fromCallable(() -> {
                    Client client = clientRepository.findById(id)
                            .orElseThrow(() -> new ResourceNotFoundException("Client", id));

                    if (request.clientName() != null) client.setClientName(request.clientName());
                    if (request.description() != null) client.setDescription(request.description());
                    if (request.status() != null)      client.setStatus(request.status());

                    Client saved = clientRepository.save(client);

                    // Invalidate cache for this client (by clearing all — simple approach)
                    apiKeyCache.invalidateAll();
                    log.info("Updated client: id={}, key={}", saved.getId(), saved.getClientKey());

                    return toResponse(saved, null);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> deleteClient(Long id) {
        return Mono.fromRunnable(() -> {
                    if (!clientRepository.existsById(id)) {
                        throw new ResourceNotFoundException("Client", id);
                    }
                    clientRepository.deleteById(id);
                    apiKeyCache.invalidateAll();
                    log.info("Deleted client: id={}", id);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    public static ClientResponse toResponse(Client client, String rawApiKey) {
        return new ClientResponse(
                client.getId(),
                client.getClientKey(),
                client.getClientName(),
                client.getDescription(),
                client.getStatus(),
                rawApiKey,                 // null on all requests except CREATE
                client.getApiKeyPrefix(),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }

    /** Simple holder for newly created client + its raw API key. */
    public record ClientWithKey(Client client, String rawApiKey) {}
}
