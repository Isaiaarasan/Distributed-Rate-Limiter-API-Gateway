# Distributed Rate Limiter & API Gateway

[![Java 21](https://img.shields.io/badge/Java-21_LTS-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2023.0.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue.svg)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A **production-grade, distributed API Gateway** with custom **Token Bucket** and **Sliding Window** rate-limiting algorithms engineered from first principles — without external rate-limiting libraries like Bucket4j or high-level annotations.

Backed by **Redis** (atomic Lua scripts for distributed state consistency), **MySQL** (multi-tenant configuration & asynchronous audit logs), **Caffeine** (sub-millisecond L1 in-memory caching), and **Spring Cloud Gateway** (reactive, non-blocking WebFlux on Netty).

---

## 📑 Table of Contents

1. [System Architecture & Request Flow](#-system-architecture--request-flow)
2. [Why Build From Scratch? (The Problem Statement)](#-why-build-from-scratch-the-problem-statement)
3. [Deep Dive: Rate Limiting Algorithms](#-deep-dive-rate-limiting-algorithms)
   - [Algorithm 1: Token Bucket](#1-token-bucket-algorithm)
   - [Algorithm 2: Sliding Window](#2-sliding-window-algorithm)
   - [Algorithm Comparison Matrix](#algorithm-comparison-matrix)
4. [Distributed State & The Race Condition Problem](#-distributed-state--the-race-condition-problem)
   - [Why Atomic Lua Scripts Are Mandatory](#why-atomic-lua-scripts-are-mandatory)
5. [Multi-Tier Caching & Data Storage Design](#-multi-tier-caching--data-storage-design)
6. [Security & API Key Authentication](#-security--api-key-authentication)
7. [Database Schema & Flyway Migrations](#-database-schema--flyway-migrations)
8. [Module Structure](#-module-structure)
9. [Prerequisites & Environment Setup](#-prerequisites--environment-setup)
10. [Step-by-Step Running Guide](#-step-by-step-running-guide)
11. [Admin REST API & Dynamic Policy Management](#-admin-rest-api--dynamic-policy-management)
12. [Hands-On Testing & Verification (curl Examples)](#-hands-on-testing--verification-curl-examples)
13. [System Design & Interview Talking Points](#-system-design--interview-talking-points)

---

## 🏗 System Architecture & Request Flow

The system acts as the single reverse proxy ingress point for all microservices (`Product`, `Order`, `User`), intercepting every HTTP request, validating credentials, calculating rate limits in single-digit milliseconds, and logging audit metadata asynchronously.

```
                                  INCOMING CLIENT REQUEST
                                (e.g. GET /api/products)
                                             │
                                             ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                   SPRING CLOUD GATEWAY (Port 8080 - Reactive WebFlux)                   │
│                                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │                    RateLimitGatewayFilter (GlobalFilter)                         │  │
│  │                                                                                  │  │
│  │  1. Extract 'X-API-Key' header                                                   │  │
│  │  2. Compute SHA-256 hash of API Key                                              │  │
│  │  3. L1 Cache Check (Caffeine) ──[MISS]──► L2 Database (MySQL 'clients' table)    │  │
│  │  4. Match Request Path & HTTP Method ──► Find ApiEndpoint                       │  │
│  │  5. Retrieve Active Rate Limit Policy ─► Select Strategy (Token Bucket/Sliding)  │  │
│  │  6. Execute Atomic Lua Script in Redis                                           │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────┬───────────────────────────────────────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       │ Decision: Allowed?                        │
                       ├────────────────────────┬──────────────────┤
                       │ [YES: ALLOWED]         │ [NO: DENIED]     │
                       ▼                        ▼                  │
┌───────────────────────────────────────────┐ ┌───────────────────────────────────────────┐
│ Forward Request Downstream                │ │ Return HTTP 429 (Too Many Requests)       │
│                                           │ │                                           │
│ Injected Headers:                         │ │ Rate Limit Headers:                       │
│ - X-Client-Id: mobile-app                 │ │ - X-RateLimit-Limit: 100                  │
│ - X-Request-Id: 4f2c-8a1e-...             │ │ - X-RateLimit-Remaining: 0                │
│ - X-RateLimit-Limit: 100                  │ │ - X-RateLimit-Algorithm: TOKEN_BUCKET     │
│ - X-RateLimit-Remaining: 87               │ │ - Retry-After: 1                          │
│ - X-RateLimit-Algorithm: TOKEN_BUCKET     │ │                                           │
│                                           │ │ Response Body (JSON):                     │
│ Targets:                                  │ │ { "success": false,                       │
│ ├── Product Service (Port 8081)           │ │   "error": "RATE_LIMIT_EXCEEDED",         │
│ ├── Order Service   (Port 8082)           │ │   "retryAfterSeconds": 1 }                │
│ └── User Service    (Port 8083)           │ └───────────────────────────────────────────┘
└─────────────────────┬─────────────────────┘                      │
                      │                                            │
                      └──────────────────────┬─────────────────────┘
                                             │
                                             ▼ (Fire-and-Forget @Async)
                        ┌───────────────────────────────────────────┐
                        │      RequestLog Table (MySQL Database)    │
                        │ - Latency (ms), Decision, IP, Timestamp   │
                        └───────────────────────────────────────────┘
```

---

## 💡 Why Build From Scratch? (The Problem Statement)

Every distributed microservices architecture requires traffic shaping, DDoS mitigation, and tiered monetization limits. In traditional enterprise projects, developers often rely on declarative annotations (`@RateLimiter`) or in-memory libraries (Bucket4j, Guava RateLimiter). 

However, in-memory rate limiting **breaks completely in a distributed environment**:

```
Request 1 ──► Gateway Instance 1 (Local Bucket: 10 tokens) ──► Allowed (Tokens left: 9)
Request 2 ──► Gateway Instance 2 (Local Bucket: 10 tokens) ──► Allowed (Tokens left: 9)
```

With 10 gateway replicas behind a load balancer, a client with a limit of 10 requests/minute can actually make **100 requests/minute**!

### Our Solution
1. **Centralized Atomic State**: State is maintained in Redis via Lua scripts, creating a unified limit across any number of Gateway instances.
2. **First-Principles Implementation**: No third-party rate-limiting dependencies; every token mathematical formula and timestamp boundary comparison is custom coded.
3. **Dynamic Hot-Reloading**: Limit updates made via Admin REST APIs take effect within seconds across all instances without gateway restarts.

---

## 🧮 Deep Dive: Rate Limiting Algorithms

### 1. Token Bucket Algorithm
- **Class**: `TokenBucketRateLimiter.java`
- **Lua Script**: `src/main/resources/scripts/token_bucket.lua`
- **Ideal Use Case**: Public APIs, Mobile apps, where burst traffic (e.g., page loads requesting multiple assets) should be accommodated gracefully.

#### Mathematical Mechanics:
1. The bucket has a fixed `capacity` $C$ and refills at a constant rate $R$ (tokens/second).
2. On every incoming request at timestamp $T_{\text{now}}$:
   $$\Delta t = T_{\text{now}} - T_{\text{last}}$$
   $$\text{Tokens}_{\text{refilled}} = \Delta t \times R$$
   $$\text{Tokens}_{\text{current}} = \min(C, \text{Tokens}_{\text{previous}} + \text{Tokens}_{\text{refilled}})$$
3. If $\text{Tokens}_{\text{current}} \ge 1$:
   - Consume 1 token: $\text{Tokens}_{\text{new}} = \text{Tokens}_{\text{current}} - 1$
   - Allow request (`allowed = 1`).
4. Else:
   - Deny request (`allowed = 0`).
   - Calculate $\text{Retry-After} = \lceil \frac{1 - \text{Tokens}_{\text{current}}}{R} \rceil$ seconds.

#### Redis State Representation (Hash):
```
Key: rl:tb:<clientKey>:<sanitizedPath>
Fields:
  ├── tokens: "42.8"               (current token count, float)
  └── lastRefill: "1724518200000"  (epoch ms of last check)
TTL: ceil(capacity / refillRate) + 60s
```

---

### 2. Sliding Window Algorithm
- **Class**: `SlidingWindowRateLimiter.java`
- **Lua Script**: `src/main/resources/scripts/sliding_window.lua`
- **Ideal Use Case**: Sensitive operations (Payment Gateways, SMS verification, Order checkout, Tiered Partner APIs) where strict mathematical boundaries are required.

#### Mathematical Mechanics:
1. Define a rolling window duration $W$ (seconds) and maximum allowable requests $M$.
2. On request at timestamp $T_{\text{now}}$:
   - **Step 1 (Purge)**: Remove all timestamps older than the sliding threshold:
     $$\text{Purge entries where score } < (T_{\text{now}} - W \times 1000)$$
   - **Step 2 (Count)**: Count the remaining timestamps in the sorted set ($K$).
   - **Step 3 (Evaluate)**:
     - If $K < M$: Add current request ID with score $T_{\text{now}}$. Allow request.
     - If $K \ge M$: Deny request. Return $\text{Retry-After} = W$ seconds.

#### Redis State Representation (Sorted Set - ZSET):
```
Key: rl:sw:<clientKey>:<sanitizedPath>
Members (ZSET):
  ├── "e8b2f91a:1724518200100" (Score: 1724518200100)
  ├── "4a1c72df:1724518201450" (Score: 1724518201450)
  └── "9c3b8801:1724518204900" (Score: 1724518204900)
TTL: windowSeconds + 1s
```

---

### Algorithm Comparison Matrix

| Feature | Token Bucket | Sliding Window | Fixed Window (Flawed) |
|---|---|---|---|
| **Burst Capacity** | ✅ Yes (up to bucket capacity) | ❌ No (strictly bounded) | ⚠️ Partial |
| **Boundary Exploitation** | 🛡️ Immune (gradual token replenishment) | 🛡️ Immune (true rolling window) | ❌ Vulnerable ($2\times$ rate at boundary) |
| **Memory Footprint** | 🟢 Minimal (2 hash fields per client) | 🟡 Moderate ($O(N)$ elements per window) | 🟢 Minimal (1 counter integer) |
| **Time Complexity** | $O(1)$ constant time | $O(\log N + M)$ (ZSET operations) | $O(1)$ constant time |
| **Primary Use Case** | Web/Mobile browsing, read APIs | B2B integrations, financial transactions | Legacy / Simple single-instance |

---

## ⚡ Distributed State & The Race Condition Problem

### The Race Condition Without Lua

In a distributed setup where multiple Gateway instances query Redis independently:

```
Instance A: GET tokens ────────► Returns 1
Instance B: GET tokens ────────► Returns 1
Instance A: (1 - 1 = 0) ──────► SET tokens = 0 ──► ALLOWS Request (Request 1 allowed)
Instance B: (1 - 1 = 0) ──────► SET tokens = 0 ──► ALLOWS Request (Request 2 allowed - VIOLATION!)
```

### The Solution: Atomic Lua Scripts
Redis executes Lua scripts in a **single-threaded, transactional context**. No other command or script can run concurrently while the Lua script is executing. 

Our `LuaScriptExecutor.java` executes the evaluation, state update, and expiration as **one atomic transaction**, guaranteeing strict distributed thread safety.

```
Gateway Instance A ──┐
                     ├──► Redis Engine [ Atomic Lua Script Execution ] ──► Exact Counter
Gateway Instance B ──┘    (Reads state -> Computes new tokens -> Writes back)
```

---

## 🗄 Multi-Tier Caching & Data Storage Design

To maintain sub-5ms gateway latency under high throughput, the system employs a **3-tier data model**:

```
Layer 1: Caffeine Cache (JVM Memory)
         └── Stores: API Key lookups (5m TTL), Route patterns (30s TTL), Policies (30s TTL)
         └── Purpose: Prevents hitting MySQL on every HTTP request (~99% DB offload)

Layer 2: MySQL 8 Database (Persistent Store)
         └── Stores: Client credentials, registered routes, policy configurations, audit logs
         └── Purpose: Source of truth, durable audit history, dynamic admin updates

Layer 3: Redis In-Memory Cluster (Distributed State)
         └── Stores: Token bucket levels, sliding window request timestamps
         └── Purpose: Ultra-fast distributed concurrency coordination
```

### Fail-Open Architecture
If Redis becomes temporarily unreachable or suffers network degradation, `LuaScriptExecutor.java` intercepts the exception gracefully and defaults to **Fail-Open** (`allowed = true`). This ensures critical business services remain accessible during infrastructure outages while logging warnings for SRE alerts.

---

## 🔐 Security & API Key Authentication

1. **Zero Plaintext Storage**: Plaintext API keys are **never saved** in the database.
2. **SHA-256 Hashing**:
   - On creation, a cryptographically random key is generated (`rl_<clientKey>_<uuid>`).
   - The key is shown to the administrator **once**.
   - Only `SHA256(rawApiKey)` is stored in the database.
3. **Safe Prefixing**:
   - The database stores an `api_key_prefix` (first 24 characters) for human identification in administrative views without exposing the secret key.
4. **Header Extraction**:
   - The client supplies their key via the HTTP header `X-API-Key: <key>`.
   - The gateway hashes the incoming header and performs an indexed lookup against `clients.api_key_hash`.

---

## 🗃 Database Schema & Flyway Migrations

The database is version-controlled using **Flyway**. On startup, migrations in `src/main/resources/db/migration/` run automatically:

```
db/migration/
├── V1__create_clients.sql              -> API client records and SHA-256 key hashes
├── V2__create_api_endpoints.sql        -> Protected routes and HTTP methods
├── V3__create_rate_limit_policies.sql  -> Configuration parameters for algorithms
├── V4__create_request_logs.sql         -> Asynchronous audit logging with composite indexes
└── V5__insert_seed_data.sql            -> Ready-to-test demo clients & policies
```

### Entity Relational Model

```
┌───────────────────────────┐         ┌───────────────────────────┐
│          clients          │         │       api_endpoints       │
├───────────────────────────┤         ├───────────────────────────┤
│ id (PK)                   │         │ id (PK)                   │
│ client_key (UQ)           │         │ name                      │
│ client_name               │         │ path                      │
│ api_key_hash (UQ, IDX)    │         │ http_method               │
│ api_key_prefix            │         │ service_name              │
│ status (ACTIVE/INACTIVE)  │         │ status (ACTIVE/INACTIVE)  │
└─────────────┬─────────────┘         └─────────────┬─────────────┘
              │                                     │
              │         ┌───────────────────────────┤
              │         │                           │
              ▼         ▼                           ▼
┌───────────────────────────────────────┐ ┌───────────────────────────────────────┐
│          rate_limit_policies          │ │             request_logs              │
├───────────────────────────────────────┤ ├───────────────────────────────────────┤
│ id (PK)                               │ │ id (PK)                               │
│ client_id (FK -> clients)             │ │ client_id (FK -> clients)             │
│ api_id (FK -> api_endpoints)          │ │ api_id (FK -> api_endpoints)          │
│ algorithm (TOKEN_BUCKET/SLIDING)      │ │ request_id (UUID)                     │
│ bucket_capacity                       │ │ ip_address                            │
│ refill_rate                           │ │ http_method                           │
│ max_requests                          │ │ request_path                          │
│ window_seconds                        │ │ http_status (200 / 429)               │
│ enabled (TINYINT)                     │ │ allowed (1 / 0)                       │
│ priority (INT)                        │ │ algorithm                             │
└───────────────────────────────────────┘ │ tokens_remaining                      │
                                          │ response_time_ms                      │
                                          │ requested_at (IDX)                    │
                                          └───────────────────────────────────────┘
```

---

## 📦 Module Structure

```
distributed-rate-limiter/
├── pom.xml                                 <-- Parent Maven multi-module POM
├── mvnw / mvnw.cmd                         <-- Maven Wrapper (zero install required)
│
├── gateway-service/                        <-- Main API Gateway Service (Port 8080)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ratelimiter/gateway/
│       │   ├── GatewayApplication.java
│       │   ├── config/                     <-- Redis & OpenAPI Swagger config
│       │   ├── filter/                     <-- RateLimitGatewayFilter (GlobalFilter)
│       │   ├── ratelimiter/
│       │   │   ├── RateLimiter.java        <-- Strategy Interface
│       │   │   ├── RateLimiterFactory.java <-- Auto-discovery Factory
│       │   │   ├── RateLimitResult.java    <-- Immutable result Record
│       │   │   ├── tokenbucket/            <-- Token Bucket algorithm
│       │   │   ├── slidingwindow/          <-- Sliding Window algorithm
│       │   │   └── redis/                  <-- Atomic Lua Script Executor
│       │   ├── domain/
│       │   │   ├── client/                 <-- Client entity, repository, service, controller
│       │   │   ├── api/                    <-- ApiEndpoint entity, repository, service, controller
│       │   │   └── policy/                 <-- RateLimitPolicy entity, repository, service, controller
│       │   ├── statistics/                 <-- Async RequestLog entity, repository, analytics API
│       │   └── common/                     <-- DTOs, Enums, Exceptions, Hash utilities
│       └── resources/
│           ├── application.yml             <-- Gateway routes & DB configuration
│           ├── scripts/
│           │   ├── token_bucket.lua        <-- Token Bucket atomic Lua script
│           │   └── sliding_window.lua      <-- Sliding Window atomic Lua script
│           └── db/migration/               <-- Flyway SQL scripts (V1 through V5)
│
└── mock-services/                          <-- Downstream Microservices
    ├── product-service/                    <-- Products API (Port 8081)
    ├── order-service/                      <-- Orders API (Port 8082)
    └── user-service/                       <-- Users API (Port 8083)
```

---

## ⚙️ Prerequisites & Environment Setup

- **Java Development Kit**: JDK 21 LTS (or 22)
- **MySQL Server**: 8.0+ running on `localhost:3306`
- **Redis Server**: 6.x or 7.x running on `localhost:6379`
- **Terminal Shell**: PowerShell, CMD, or Bash

---

## 🚀 Step-by-Step Running Guide

### Step 1: Configure Database Credentials
Open `gateway-service/src/main/resources/application.yml` and set your MySQL password:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rate_limiter_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
    username: root
    password: YOUR_MYSQL_PASSWORD  # <-- update here
```

### Step 2: Build & Verify the Multi-Module Project
From the repository root:
```powershell
.\mvnw.cmd clean install -DskipTests
```

### Step 3: Launch Downstream Mock Microservices
Open 3 separate terminal tabs:

**Terminal 1 — Product Service (Port 8081):**
```powershell
cd "mock-services\product-service"
..\..\mvnw.cmd spring-boot:run
```

**Terminal 2 — Order Service (Port 8082):**
```powershell
cd "mock-services\order-service"
..\..\mvnw.cmd spring-boot:run
```

**Terminal 3 — User Service (Port 8083):**
```powershell
cd "mock-services\user-service"
..\..\mvnw.cmd spring-boot:run
```

### Step 4: Launch the API Gateway (Port 8080)
Open a 4th terminal tab:
```powershell
cd "gateway-service"
..\mvnw.cmd spring-boot:run
```

---

## 🖥 Admin REST API & Dynamic Policy Management

The gateway provides interactive OpenAPI documentation accessible via your browser at:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

### Key Administrative Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/admin/clients` | Register a new client and generate a secure API Key |
| `GET` | `/api/admin/clients` | List all registered clients |
| `POST` | `/api/admin/apis` | Register a new downstream route to protect |
| `POST` | `/api/admin/rate-limits` | Attach a rate limit policy to a Client + API pair |
| `PUT` | `/api/admin/rate-limits/{id}` | **Live Hot-Update** rate limits (invalidates cache instantly) |
| `GET` | `/api/admin/statistics` | Global analytics (Total requests, Allowed vs Denied, Latencies) |

---

## 🧪 Hands-On Testing & Verification (curl Examples)

The database comes pre-seeded with 3 demo clients and policies ready for immediate testing:

| Client Key | API Key | Target Route | Algorithm & Limit |
|---|---|---|---|
| `mobile-app` | `rl_mobile-app_demo_key_mobile_app_001` | `/api/products` | **Token Bucket**: 100 tokens, 2.0/sec refill |
| `web-app` | `rl_web-app_demo_key_web_app_00000001` | `/api/orders` | **Sliding Window**: 50 requests / 60 seconds |
| `partner-api` | `rl_partner-api_demo_key_partner_0001` | `/api/products` | **Token Bucket**: 10 tokens (strict burst) |

---

### Test 1: Successful Request (Allowed)
```powershell
curl -i -H "X-API-Key: rl_mobile-app_demo_key_mobile_app_001" http://localhost:8080/api/products
```

**Response (HTTP 200 OK):**
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Algorithm: TOKEN_BUCKET
X-Powered-By: Distributed-Rate-Limiter-Gateway
Content-Type: application/json

{
  "service": "product-service",
  "clientId": "mobile-app",
  "data": [ ... ],
  "count": 5
}
```

---

### Test 2: Triggering Rate Limit Denial (HTTP 429)
Execute 15 rapid requests against `partner-api` (capacity: 10 tokens):

```powershell
for ($i = 1; $i -le 15; $i++) {
    curl.exe -s -o nul -w "Request $i status: %{http_code}`n" -H "X-API-Key: rl_partner-api_demo_key_partner_0001" http://localhost:8080/api/products
}
```

**Output:**
```
Request 1 status: 200
Request 2 status: 200
...
Request 10 status: 200
Request 11 status: 429
Request 12 status: 429
```

**Response Body on 429:**
```json
{
  "success": false,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. You have exceeded your rate limit.",
  "clientKey": "partner-api",
  "path": "/api/products",
  "algorithm": "TOKEN_BUCKET",
  "retryAfterSeconds": 6,
  "timestamp": "2026-08-24T23:45:00.123"
}
```

---

### Test 3: Live Rate Limit Update Without Restarts
Increase `partner-api`'s bucket capacity from 10 to 500 on the fly:

```powershell
curl -X PUT http://localhost:8080/api/admin/rate-limits/4 `
     -H "Content-Type: application/json" `
     -d '{"clientId": 3, "apiId": 1, "algorithm": "TOKEN_BUCKET", "bucketCapacity": 500, "refillRate": 10.0}'
```

*The gateway immediately invalidates the L1 cache for this rule and applies the new 500 token limit on the very next request!*

---

### Test 4: View Real-Time Gateway Analytics
```powershell
curl http://localhost:8080/api/admin/statistics
```

**Response:**
```json
{
  "success": true,
  "data": {
    "totalRequests": 128,
    "allowedRequests": 115,
    "deniedRequests": 13,
    "successRate": 89.84,
    "avgResponseTimeMs": 4.12,
    "requestsLastHour": 128,
    "topClients": {
      "partner-api": 45,
      "mobile-app": 83
    }
  }
}
```

---

## 🎓 System Design & Interview Talking Points

When presenting this project in technical interviews, emphasize these architectural design decisions:

1. **Why not just use Spring annotations or Resilience4j?**
   - Annotations and Resilience4j execute in-memory within a single JVM. In microservices with 5+ gateway instances, in-memory counters multiply allowed traffic by $5\times$.
2. **Why are Lua scripts required instead of normal Redis commands (MULTI/EXEC or GET/SET)?**
   - Normal `GET` followed by `SET` introduces a race condition between reading and writing across distributed instances. `MULTI/EXEC` cannot perform conditional logic based on the result of a previous read. Lua scripts run atomically inside Redis's single-threaded event loop.
3. **How do you prevent Redis from exhausting memory in Sliding Window?**
   - The Lua script executes `ZREMRANGEBYSCORE` on every request to purge timestamps outside the window, keeping memory bounded to $O(\text{maxRequests})$ per client. An explicit TTL (`EXPIRE`) cleans up idle keys.
4. **How do you achieve high throughput without overloading MySQL?**
   - The gateway uses a 2-tier caching model: hot lookups (API Key hashes, routes, policies) are held in Caffeine L1 in-memory caches with short TTLs (30s to 5min), eliminating ~99% of database read overhead. Audit logs are written fire-and-forget via `@Async`.
5. **How does the system handle Redis downtime?**
   - The gateway implements a **Fail-Open** pattern. If the Redis connection drops, the gateway catches the exception, logs a high-priority alert, and allows traffic through rather than causing an outage for the downstream services.
