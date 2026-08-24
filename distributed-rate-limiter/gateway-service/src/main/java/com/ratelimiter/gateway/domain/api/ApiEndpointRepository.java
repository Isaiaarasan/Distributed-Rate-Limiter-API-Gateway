package com.ratelimiter.gateway.domain.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

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
