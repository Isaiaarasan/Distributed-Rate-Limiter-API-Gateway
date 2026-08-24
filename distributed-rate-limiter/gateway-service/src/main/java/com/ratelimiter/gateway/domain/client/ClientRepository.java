package com.ratelimiter.gateway.domain.client;

import com.ratelimiter.gateway.common.enums.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByApiKeyHash(String apiKeyHash);
    Optional<Client> findByClientKey(String clientKey);
    boolean existsByClientKey(String clientKey);
    List<Client> findByStatus(ClientStatus status);
    List<Client> findByStatusOrderByCreatedAtDesc(ClientStatus status);
}
