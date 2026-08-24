-- ============================================================
-- V3: Create rate_limit_policies table
-- ============================================================
-- The heart of the system.
-- Ties a client to an API endpoint with a specific algorithm and limits.
--
-- TOKEN_BUCKET algorithm:
--   bucket_capacity = max tokens in bucket
--   refill_rate     = tokens added per second
--
-- SLIDING_WINDOW algorithm:
--   max_requests    = max requests allowed
--   window_seconds  = rolling window size in seconds
--
-- priority: higher value = evaluated first (used when multiple policies exist)
-- enabled:  quick toggle without deleting (live rate limit on/off switch)
-- ============================================================

CREATE TABLE IF NOT EXISTS rate_limit_policies (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    client_id       BIGINT          NOT NULL COMMENT 'FK → clients.id',
    api_id          BIGINT          NOT NULL COMMENT 'FK → api_endpoints.id',
    algorithm       VARCHAR(50)     NOT NULL COMMENT 'TOKEN_BUCKET | SLIDING_WINDOW',

    -- Token Bucket parameters
    bucket_capacity INT             NULL     COMMENT '[TOKEN_BUCKET] Max tokens in bucket',
    refill_rate     DECIMAL(10,4)   NULL     COMMENT '[TOKEN_BUCKET] Tokens added per second (float)',

    -- Sliding Window parameters
    max_requests    INT             NULL     COMMENT '[SLIDING_WINDOW] Max requests in window',
    window_seconds  INT             NULL     COMMENT '[SLIDING_WINDOW] Window duration in seconds',

    -- Control
    enabled         TINYINT(1)      NOT NULL DEFAULT 1   COMMENT '1=enabled, 0=disabled',
    priority        INT             NOT NULL DEFAULT 0   COMMENT 'Higher = evaluated first',

    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_rate_limit_policies     PRIMARY KEY (id),
    CONSTRAINT uq_policy_client_api       UNIQUE KEY  (client_id, api_id),
    CONSTRAINT fk_policy_client           FOREIGN KEY (client_id)
                                          REFERENCES  clients(id)
                                          ON DELETE   CASCADE
                                          ON UPDATE   CASCADE,
    CONSTRAINT fk_policy_api              FOREIGN KEY (api_id)
                                          REFERENCES  api_endpoints(id)
                                          ON DELETE   CASCADE
                                          ON UPDATE   CASCADE,
    INDEX      idx_policy_client          (client_id),
    INDEX      idx_policy_api             (api_id),
    INDEX      idx_policy_enabled         (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Rate limit policies: per-client, per-API, with algorithm-specific parameters';
