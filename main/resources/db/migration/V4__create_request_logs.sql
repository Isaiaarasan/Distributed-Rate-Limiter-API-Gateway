-- ============================================================
-- V4: Create request_logs table
-- ============================================================
-- Immutable audit log of every request that passes through the gateway.
-- Written asynchronously (non-blocking) from the request path.
--
-- Key fields:
--   allowed         = true if request was forwarded, false if rejected (429)
--   http_status     = 200 (allowed) or 429 (rate limited) or 401/403 (auth)
--   tokens_remaining = rate limit headroom at time of decision
--   response_time_ms = end-to-end gateway processing time
--
-- Indexes are optimized for the most common analytics queries:
--   - Count by client (per-client dashboard)
--   - Count by api (per-API dashboard)
--   - Filter by time range (last hour, last 24h)
--   - Filter allowed vs denied
-- ============================================================

CREATE TABLE IF NOT EXISTS request_logs (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    client_id        BIGINT        NULL     COMMENT 'FK → clients.id (nullable for unauth requests)',
    api_id           BIGINT        NULL     COMMENT 'FK → api_endpoints.id (nullable if no match)',
    request_id       VARCHAR(36)   NOT NULL COMMENT 'UUID for request tracing (correlation ID)',
    ip_address       VARCHAR(45)   NULL     COMMENT 'Client IP (IPv4 or IPv6)',
    http_method      VARCHAR(10)   NULL     COMMENT 'GET | POST | PUT | DELETE | ...',
    request_path     VARCHAR(500)  NULL     COMMENT 'Incoming request path',
    http_status      INT           NULL     COMMENT '200 allowed | 429 rate-limited | 401/403 auth',
    allowed          TINYINT(1)    NOT NULL COMMENT '1=forwarded to backend, 0=rejected',
    algorithm        VARCHAR(50)   NULL     COMMENT 'TOKEN_BUCKET | SLIDING_WINDOW',
    tokens_remaining INT           NULL     COMMENT 'Remaining tokens/slots after this request',
    response_time_ms BIGINT        NULL     COMMENT 'End-to-end processing time in milliseconds',
    requested_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_request_logs         PRIMARY KEY (id),
    INDEX      idx_log_client          (client_id),
    INDEX      idx_log_api             (api_id),
    INDEX      idx_log_requested_at    (requested_at),
    INDEX      idx_log_allowed         (allowed),
    INDEX      idx_log_http_status     (http_status),
    -- Composite index for common analytics: client + time range
    INDEX      idx_log_client_time     (client_id, requested_at),
    -- Composite index for denial analytics
    INDEX      idx_log_allowed_time    (allowed, requested_at),

    CONSTRAINT fk_log_client FOREIGN KEY (client_id)
               REFERENCES clients(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_log_api    FOREIGN KEY (api_id)
               REFERENCES api_endpoints(id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable audit log of all gateway requests for analytics and debugging';
