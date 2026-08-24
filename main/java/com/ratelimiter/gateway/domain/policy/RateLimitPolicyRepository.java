package com.ratelimiter.gateway.domain.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for RateLimitPolicy entities.
 *
 * The findActivePolicyForClientAndApi query is the CRITICAL HOT PATH:
 *   It's called on every request to determine the rate limit rule.
 *   Results are cached in RateLimitPolicyService with a short TTL.
 */
@Repository
public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicy, Long> {

    /**
     * Find the highest-priority active policy for a given client and API endpoint.
     * Result is cached in RateLimitPolicyService after the first DB hit.
     */
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

    /** Admin: list all policies for a given client. */
    @Query("""
            SELECT p FROM RateLimitPolicy p
            WHERE p.client.id = :clientId
            ORDER BY p.priority DESC, p.createdAt DESC
            """)
    List<RateLimitPolicy> findByClientId(@Param("clientId") Long clientId);

    /** Admin: list all policies for a given API endpoint. */
    @Query("""
            SELECT p FROM RateLimitPolicy p
            WHERE p.apiEndpoint.id = :apiId
            ORDER BY p.priority DESC, p.createdAt DESC
            """)
    List<RateLimitPolicy> findByApiId(@Param("apiId") Long apiId);

    /** Prevent duplicate policies for the same client+api pair. */
    boolean existsByClientIdAndApiEndpointId(Long clientId, Long apiEndpointId);
}
