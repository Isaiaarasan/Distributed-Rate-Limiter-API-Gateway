package com.ratelimiter.gateway.domain.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, Long> {

    @Query("""
            SELECT p FROM RateLimitPolicy p
            WHERE p.client.id = :clientId
              AND p.apiEndpoint.id = :apiId
              AND p.enabled = true
            ORDER BY p.priority DESC
            """)
    Optional<RateLimitPolicy> findActivePolicyForClientAndApi(
            @Param("clientId") Long clientId,
            @Param("apiId") Long apiId
    );

    @Query("SELECT p FROM RateLimitPolicy p WHERE p.client.id = :clientId ORDER BY p.priority DESC, p.createdAt DESC")
    List<RateLimitPolicy> findByClientId(@Param("clientId") Long clientId);

    @Query("SELECT p FROM RateLimitPolicy p WHERE p.apiEndpoint.id = :apiId ORDER BY p.priority DESC, p.createdAt DESC")
    List<RateLimitPolicy> findByApiId(@Param("apiId") Long apiId);

    boolean existsByClientIdAndApiEndpointId(Long clientId, Long apiEndpointId);
}
