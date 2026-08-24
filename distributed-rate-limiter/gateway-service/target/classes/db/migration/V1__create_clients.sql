-- ============================================================
-- V1: Create clients table
-- ============================================================
-- Stores API consumers. The api_key_hash column is the lookup
-- column used on every incoming request (indexed for speed).
-- The raw API key is NEVER stored here — only the SHA-256 hash.
-- ============================================================

CREATE TABLE IF NOT EXISTS clients (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    client_key     VARCHAR(100)    NOT NULL COMMENT 'Unique human-readable ID, e.g. mobile-app',
    client_name    VARCHAR(255)    NOT NULL COMMENT 'Display name',
    api_key_hash   VARCHAR(64)     NOT NULL COMMENT 'SHA-256 hash of the raw API key',
    api_key_prefix VARCHAR(30)     NULL     COMMENT 'First ~24 chars of key for admin display',
    description    VARCHAR(1000)   NULL,
    status         VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE | SUSPENDED',
    created_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_clients         PRIMARY KEY (id),
    CONSTRAINT uq_client_key      UNIQUE KEY  (client_key),
    CONSTRAINT uq_client_api_hash UNIQUE KEY  (api_key_hash),
    INDEX      idx_client_status  (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API consumers: each client gets a unique API key for gateway authentication';
