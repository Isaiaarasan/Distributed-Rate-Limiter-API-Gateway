package com.ratelimiter.gateway.domain.client;

import com.ratelimiter.gateway.common.enums.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Client entities.
 *
 * Performance note:
 *   - findByApiKeyHash is the HOT PATH — called on every incoming request.
 *   - It is indexed (idx_client_api_key_hash) and cached in ClientService.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Primary lookup: used on every request after hashing the X-API-Key header. */
    Optional<Client> findByApiKeyHash(String apiKeyHash);

    /** Used for admin uniqueness checks. */
    Optional<Client> findByClientKey(String clientKey);

    /** Used to check for duplicates before creation. */
    boolean existsByClientKey(String clientKey);

    /** Admin: filter clients by status. */
    List<Client> findByStatus(ClientStatus status);

    /** Admin: list all active clients. */
    List<Client> findByStatusOrderByCreatedAtDesc(ClientStatus status);
}
