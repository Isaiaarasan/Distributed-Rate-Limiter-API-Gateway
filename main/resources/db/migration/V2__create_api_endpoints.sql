-- ============================================================
-- V2: Create api_endpoints table
-- ============================================================
-- Stores the API routes that can be protected by rate limiting.
-- The path + http_method combination uniquely identifies an endpoint.
-- httpMethod = 'ANY' matches all HTTP methods for a given path.
-- ============================================================

CREATE TABLE IF NOT EXISTS api_endpoints (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    name         VARCHAR(255)  NOT NULL COMMENT 'Human-readable name, e.g. Get Products',
    path         VARCHAR(500)  NOT NULL COMMENT 'URL path prefix, e.g. /api/products',
    http_method  VARCHAR(10)   NOT NULL COMMENT 'GET | POST | PUT | DELETE | PATCH | ANY',
    service_name VARCHAR(255)  NULL     COMMENT 'Logical backend service name, e.g. product-service',
    target_url   VARCHAR(500)  NULL     COMMENT 'Backend URL, e.g. http://localhost:8081',
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE',
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_api_endpoints        PRIMARY KEY (id),
    CONSTRAINT uq_api_path_method      UNIQUE KEY  (path, http_method),
    INDEX      idx_api_status          (status),
    INDEX      idx_api_path            (path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Protected API endpoints registered with the gateway';
