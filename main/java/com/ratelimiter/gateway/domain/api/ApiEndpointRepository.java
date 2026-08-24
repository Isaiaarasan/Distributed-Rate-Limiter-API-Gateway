package com.ratelimiter.gateway.domain.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ApiEndpoint entities.
 *
 * The findMatchingEndpoint query is the key operation:
 *   Given an incoming request path "/api/products/123" and method "GET",
 *   it finds the best matching endpoint by:
 *     1. Exact path match OR prefix match (path is a prefix of the request path)
 *     2. Method matches OR endpoint allows ANY method
 *     3. Ordered by path length DESC (most specific match first)
 */
@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    /**
     * Find the most specific active endpoint matching the given path and HTTP method.
     * Used in the hot path of the gateway filter (cached in ApiEndpointService).
     *
     * Path matching: exact OR the stored path is a prefix of the incoming path.
     * Method matching: exact OR the endpoint is configured as "ANY".
     */
    @Query("""
            SELECT a FROM ApiEndpoint a
            WHERE a.status = 'ACTIVE'
              AND (:path = a.path OR :path LIKE CONCAT(a.path, '%'))
              AND (a.httpMethod = :method OR a.httpMethod = 'ANY')
            ORDER BY LENGTH(a.path) DESC
            """)
    List<ApiEndpoint> findMatchingEndpoints(
            @Param("path") String path,
            @Param("method") String method
    );

    Optional<ApiEndpoint> findByPathAndHttpMethod(String path, String httpMethod);

    List<ApiEndpoint> findByStatusOrderByCreatedAtDesc(String status);

    boolean existsByPathAndHttpMethod(String path, String httpMethod);
}
