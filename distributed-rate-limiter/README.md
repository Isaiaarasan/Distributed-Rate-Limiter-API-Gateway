# Distributed Rate Limiter & API Gateway

A **production-grade distributed API Gateway** implementing **Token Bucket** and **Sliding Window** rate-limiting algorithms from first principles — no Bucket4j, no Spring annotations.

## Architecture

```
Client
  ↓
API Gateway (port 8080) ← Spring Cloud Gateway
  ↓
RateLimitGatewayFilter (GlobalFilter)
  ↓
  ├── MySQL (client/api/policy config)
  ├── Redis  (atomic Lua scripts → Token Bucket / Sliding Window state)
  │
  ↓ If ALLOWED → forward to backend
  ├── Product Service (port 8081)
  ├── Order Service   (port 8082)
  └── User Service    (port 8083)
  ↓ If DENIED → HTTP 429 + Retry-After
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 LTS |
| Gateway | Spring Cloud Gateway 4.x (WebFlux/Reactive) |
| Database | MySQL 8.x + Spring Data JPA + Flyway |
| Distributed State | Redis 7.x + Lua scripts (atomic) |
| Cache | Caffeine (in-process) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9+ / Maven Wrapper (`mvnw`) |

## Prerequisites

Before running, install these on Windows:

### 1. Java 22
Download from: https://adoptium.net/
Verify: `java -version`

### 2. Maven 3.9+
Download from: https://maven.apache.org/download.cgi
Add to PATH. Verify: `mvn -version`

### 3. MySQL 8.x
Download: https://dev.mysql.com/downloads/mysql/
- Set root password during installation
- Start MySQL service: `net start mysql80` (in admin terminal)
- Verify: `mysql -u root -p`

### 4. Redis for Windows
Option A (WSL): `wsl --install` then `sudo apt install redis-server`
Option B (Native): https://github.com/tporadowski/redis/releases
- Download the latest `.msi` and install
- Start: `redis-server`
- Verify: `redis-cli ping` → should return `PONG`

## Setup

### Step 1: Configure MySQL password

Edit `gateway-service/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    password: YOUR_MYSQL_ROOT_PASSWORD  # ← change this
```

### Step 2: Build the entire project
```powershell
cd "I:\Projects\API MONITOR\Distributed Rate Limiter & API Gateway\distributed-rate-limiter"
.\mvnw clean install -DskipTests
```

### Step 3: Start Redis & MySQL
Ensure Redis (`localhost:6379`) and MySQL (`localhost:3306`) are running.

### Step 4: Start the mock backend services (3 separate terminals)

**Terminal 1 — Product Service (port 8081):**
```powershell
cd "I:\Projects\API MONITOR\Distributed Rate Limiter & API Gateway\distributed-rate-limiter\mock-services\product-service"
.\mvnw spring-boot:run
```

**Terminal 2 — Order Service (port 8082):**
```powershell
cd "I:\Projects\API MONITOR\Distributed Rate Limiter & API Gateway\distributed-rate-limiter\mock-services\order-service"
.\mvnw spring-boot:run
```

**Terminal 3 — User Service (port 8083):**
```powershell
cd "I:\Projects\API MONITOR\Distributed Rate Limiter & API Gateway\distributed-rate-limiter\mock-services\user-service"
.\mvnw spring-boot:run
```

### Step 5: Start the API Gateway (port 8080)
```powershell
cd "I:\Projects\API MONITOR\Distributed Rate Limiter & API Gateway\distributed-rate-limiter\gateway-service"
.\mvnw spring-boot:run
```

**On startup, Flyway automatically creates:**
- `rate_limiter_db` database
- `clients`, `api_endpoints`, `rate_limit_policies`, `request_logs` tables
- Demo seed data (3 clients, 6 API endpoints, 5 policies)

## Swagger UI

Open in your browser:
```
http://localhost:8080/swagger-ui.html
```

## Testing the Rate Limiter

### Demo API Keys (from seed data)

| Client | API Key | Limit |
|--------|---------|-------|
| mobile-app | `rl_mobile-app_demo_key_mobile_app_001` | 100 tokens, refill 2/sec (Token Bucket) |
| web-app | `rl_web-app_demo_key_web_app_00000001` | 50 req/min (Sliding Window) |
| partner-api | `rl_partner-api_demo_key_partner_0001` | 10 tokens, strict (Token Bucket) |

### Test 1: Basic request (should return 200)
```powershell
curl -H "X-API-Key: rl_mobile-app_demo_key_mobile_app_001" http://localhost:8080/api/products
```

### Test 2: Trigger rate limit (partner-api has very low limit)
```powershell
# Run 15 times — first 10 succeed, rest get 429
for /L %i in (1,1,15) do curl -s -o nul -w "Request %i: %%{http_code}\n" -H "X-API-Key: rl_partner-api_demo_key_partner_0001" http://localhost:8080/api/products
```

### Test 3: Check rate limit headers
```powershell
curl -v -H "X-API-Key: rl_mobile-app_demo_key_mobile_app_001" http://localhost:8080/api/products 2>&1 | findstr "X-RateLimit"
```

### Test 4: Create a new client via Admin API
```powershell
curl -X POST http://localhost:8080/api/admin/clients `
     -H "Content-Type: application/json" `
     -d "{\"clientKey\": \"my-test-app\", \"clientName\": \"My Test Application\", \"description\": \"Test client\"}"
```

### Test 5: Create a rate limit policy
```powershell
curl -X POST http://localhost:8080/api/admin/rate-limits `
     -H "Content-Type: application/json" `
     -d "{\"clientId\": 1, \"apiId\": 1, \"algorithm\": \"TOKEN_BUCKET\", \"bucketCapacity\": 5, \"refillRate\": 0.5}"
```

### Test 6: View statistics
```powershell
curl http://localhost:8080/api/admin/statistics
```

### Test 7: LIVE limit change (no restart needed)
```powershell
# Change mobile-app's product limit from 100 to 200 tokens
curl -X PUT http://localhost:8080/api/admin/rate-limits/1 `
     -H "Content-Type: application/json" `
     -d "{\"bucketCapacity\": 200, \"refillRate\": 5.0}"
```

### Test 8: Distributed test (multiple gateway instances)
```powershell
# Terminal A: 
java -jar gateway-service/target/gateway-service.jar --server.port=8080

# Terminal B:
java -jar gateway-service/target/gateway-service.jar --server.port=8081

# Split load across both — Redis keeps a single shared counter
```

## Project Structure

```
distributed-rate-limiter/
├── gateway-service/                  ← Main API Gateway
│   ├── src/main/java/com/ratelimiter/gateway/
│   │   ├── GatewayApplication.java
│   │   ├── config/                   ← Redis, OpenAPI config
│   │   ├── filter/                   ← RateLimitGatewayFilter (core)
│   │   ├── ratelimiter/
│   │   │   ├── RateLimiter.java      ← Strategy interface
│   │   │   ├── RateLimiterFactory.java
│   │   │   ├── RateLimitResult.java
│   │   │   ├── tokenbucket/          ← Token Bucket implementation
│   │   │   ├── slidingwindow/        ← Sliding Window implementation
│   │   │   └── redis/               ← Atomic Lua script executor
│   │   ├── domain/
│   │   │   ├── client/               ← Client CRUD + API key management
│   │   │   ├── api/                  ← API endpoint registration
│   │   │   └── policy/               ← Rate limit policy CRUD
│   │   ├── statistics/               ← Request logging + analytics
│   │   └── common/                   ← DTOs, enums, exceptions, utils
│   └── src/main/resources/
│       ├── application.yml
│       ├── scripts/
│       │   ├── token_bucket.lua      ← Atomic Token Bucket Lua script
│       │   └── sliding_window.lua    ← Atomic Sliding Window Lua script
│       └── db/migration/             ← Flyway SQL migrations (V1-V5)
├── mock-services/
│   ├── product-service/ (port 8081)
│   ├── order-service/   (port 8082)
│   └── user-service/    (port 8083)
└── pom.xml (parent)
```

## Key Design Points for Interviews

1. **Why Lua scripts?**
   Without atomicity, two concurrent instances can both read `tokens=1`, both subtract 1, and both allow requests — violating the limit. Lua scripts execute as a single unit in Redis.

2. **Why Redis AND MySQL?**
   MySQL stores CONFIGURATION (what the limit IS). Redis stores RUNTIME STATE (current token count). High-frequency counters in MySQL would be a bottleneck.

3. **Why Caffeine cache?**
   MySQL is queried per request without a cache. At 1000 RPS, that's 1000 MySQL queries/sec. Caffeine caches client and policy lookups (30s TTL) reducing DB load by ~99%.

4. **Fail-open design?**
   If Redis goes down, `LuaScriptExecutor` catches the exception and returns `allowed=true`. Availability is prioritized over strict rate limiting during infrastructure failures.

5. **Live configuration changes?**
   When you call `PUT /api/admin/rate-limits/{id}`, the policy cache is immediately invalidated. New requests use the new limits within milliseconds. No restart needed.
