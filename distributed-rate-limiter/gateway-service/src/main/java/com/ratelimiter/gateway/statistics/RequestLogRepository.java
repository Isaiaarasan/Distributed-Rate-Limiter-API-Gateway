package com.ratelimiter.gateway.statistics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    long countByAllowed(boolean allowed);

    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.requestedAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.allowed = false AND r.requestedAt >= :since")
    long countDeniedSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(AVG(r.responseTimeMs), 0) FROM RequestLog r WHERE r.requestedAt >= :since")
    Double avgResponseTimeSince(@Param("since") LocalDateTime since);

    long countByClientId(Long clientId);
    long countByClientIdAndAllowed(Long clientId, boolean allowed);
    long countByApiId(Long apiId);
    long countByApiIdAndAllowed(Long apiId, boolean allowed);

    @Query("SELECT r FROM RequestLog r WHERE r.clientId = :clientId ORDER BY r.requestedAt DESC")
    List<RequestLog> findRecentByClientId(@Param("clientId") Long clientId,
                                          org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT r.clientId, COUNT(r) AS total
            FROM RequestLog r WHERE r.requestedAt >= :since
            GROUP BY r.clientId ORDER BY total DESC
            """)
    List<Object[]> findTopClientsSince(@Param("since") LocalDateTime since,
                                       org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT r.requestPath, COUNT(r) AS total
            FROM RequestLog r WHERE r.requestedAt >= :since
            GROUP BY r.requestPath ORDER BY total DESC
            """)
    List<Object[]> findTopApiPathsSince(@Param("since") LocalDateTime since,
                                        org.springframework.data.domain.Pageable pageable);
}
