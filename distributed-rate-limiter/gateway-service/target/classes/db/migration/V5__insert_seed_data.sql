-- ============================================================
-- V5: Seed data for immediate testing
-- ============================================================
-- This migration inserts demo clients, API endpoints, and
-- rate limit policies so you can test immediately after startup.
--
-- Demo API keys (use these in X-API-Key header):
--
--   mobile-app  → rl_mobile-app_demo_key_mobile_app_001
--   web-app     → rl_web-app_demo_key_web_app_00000001
--   partner-api → rl_partner-api_demo_key_partner_0001
--
-- These are SHA-256 hashed in the api_key_hash column below.
-- To use your own keys, create clients via the admin API.
-- ============================================================

-- ── API Endpoints ─────────────────────────────────────────────────────────────
INSERT INTO api_endpoints (name, path, http_method, service_name, target_url, status) VALUES
    ('Get All Products',   '/api/products',       'GET',  'product-service', 'http://localhost:8081', 'ACTIVE'),
    ('Get Product by ID',  '/api/products',       'ANY',  'product-service', 'http://localhost:8081', 'ACTIVE'),
    ('Get All Orders',     '/api/orders',         'GET',  'order-service',   'http://localhost:8082', 'ACTIVE'),
    ('Create Order',       '/api/orders',         'POST', 'order-service',   'http://localhost:8082', 'ACTIVE'),
    ('Get All Users',      '/api/users',          'GET',  'user-service',    'http://localhost:8083', 'ACTIVE'),
    ('Get User by ID',     '/api/users',          'ANY',  'user-service',    'http://localhost:8083', 'ACTIVE');

-- ── Demo Clients ──────────────────────────────────────────────────────────────
-- IMPORTANT: api_key_hash values below are SHA-256 of the demo keys shown above.
-- To generate your own: echo -n "your_api_key" | sha256sum
--
-- Demo key:  rl_mobile-app_demo_key_mobile_app_001
-- SHA-256:   pre-computed below (replace with actual hash if using real demo keys)
--
-- For the demo to work simply use the admin API to create clients:
-- POST /api/admin/clients → get back a real API key

INSERT INTO clients (client_key, client_name, api_key_hash, api_key_prefix, description, status) VALUES
    ('mobile-app',
     'Mobile Application',
     SHA2('rl_mobile-app_demo_key_mobile_app_001', 256),
     'rl_mobile-app_demo_ke...',
     'iOS and Android mobile app. Token Bucket: 100 tokens, 2/sec refill.',
     'ACTIVE'),

    ('web-app',
     'Web Application',
     SHA2('rl_web-app_demo_key_web_app_00000001', 256),
     'rl_web-app_demo_key_w...',
     'React web application. Sliding Window: 50 req/min.',
     'ACTIVE'),

    ('partner-api',
     'Partner API Integration',
     SHA2('rl_partner-api_demo_key_partner_0001', 256),
     'rl_partner-api_demo_k...',
     'External partner with strict limits. Sliding Window: 10 req/min.',
     'ACTIVE');

-- ── Rate Limit Policies ───────────────────────────────────────────────────────
-- mobile-app: Token Bucket on /api/products (ANY method)
INSERT INTO rate_limit_policies (client_id, api_id, algorithm, bucket_capacity, refill_rate, max_requests, window_seconds, enabled, priority)
SELECT c.id, a.id, 'TOKEN_BUCKET', 100, 2.0, NULL, NULL, 1, 10
FROM clients c, api_endpoints a
WHERE c.client_key = 'mobile-app' AND a.path = '/api/products' AND a.http_method = 'GET';

-- mobile-app: Sliding Window on /api/orders (GET)
INSERT INTO rate_limit_policies (client_id, api_id, algorithm, bucket_capacity, refill_rate, max_requests, window_seconds, enabled, priority)
SELECT c.id, a.id, 'SLIDING_WINDOW', NULL, NULL, 20, 60, 1, 5
FROM clients c, api_endpoints a
WHERE c.client_key = 'mobile-app' AND a.path = '/api/orders' AND a.http_method = 'GET';

-- web-app: Sliding Window on /api/products (GET)
INSERT INTO rate_limit_policies (client_id, api_id, algorithm, bucket_capacity, refill_rate, max_requests, window_seconds, enabled, priority)
SELECT c.id, a.id, 'SLIDING_WINDOW', NULL, NULL, 50, 60, 1, 10
FROM clients c, api_endpoints a
WHERE c.client_key = 'web-app' AND a.path = '/api/products' AND a.http_method = 'GET';

-- partner-api: Strict Token Bucket on /api/products (GET)
INSERT INTO rate_limit_policies (client_id, api_id, algorithm, bucket_capacity, refill_rate, max_requests, window_seconds, enabled, priority)
SELECT c.id, a.id, 'TOKEN_BUCKET', 10, 0.1667, NULL, NULL, 1, 10
FROM clients c, api_endpoints a
WHERE c.client_key = 'partner-api' AND a.path = '/api/products' AND a.http_method = 'GET';

-- partner-api: Very strict Sliding Window on /api/orders (POST)
INSERT INTO rate_limit_policies (client_id, api_id, algorithm, bucket_capacity, refill_rate, max_requests, window_seconds, enabled, priority)
SELECT c.id, a.id, 'SLIDING_WINDOW', NULL, NULL, 5, 60, 1, 10
FROM clients c, api_endpoints a
WHERE c.client_key = 'partner-api' AND a.path = '/api/orders' AND a.http_method = 'POST';
