--[[
  Sliding Window Rate Limiter - Redis Lua Script
  ────────────────────────────────────────────────
  Executes atomically inside Redis. No other command can interleave.

  Uses a Redis Sorted Set (ZSET) where:
    Member = "{requestId}:{timestamp}"   (unique per request)
    Score  = timestamp in milliseconds   (used for range removal)

  KEYS[1]  = Redis sorted set key (e.g., "rl:sw:mobile-app::api:products")
  ARGV[1]  = maxRequests   (max allowed requests within the window)
  ARGV[2]  = windowSeconds (rolling window duration in seconds)
  ARGV[3]  = now           (current time in milliseconds)
  ARGV[4]  = requestId     (unique short ID for this request)

  Returns: {allowed, remaining}
    allowed   → 1 = allow the request, 0 = deny with 429
    remaining → request slots remaining in current window
--]]

local key          = KEYS[1]
local maxRequests  = tonumber(ARGV[1])
local windowMs     = tonumber(ARGV[2]) * 1000   -- convert seconds to ms
local now          = tonumber(ARGV[3])
local requestId    = ARGV[4]

-- Step 1: Remove all entries OLDER than the window boundary
-- windowStart = now - windowMs (oldest allowable timestamp)
local windowStart = now - windowMs
redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

-- Step 2: Count remaining requests in the current window
local count = tonumber(redis.call('ZCARD', key))

local allowed   = 0
local remaining = math.max(0, maxRequests - count)

-- Step 3: If under the limit, record this request
if count < maxRequests then
    -- Member is unique: requestId + timestamp (prevents collision in same millisecond)
    local member = requestId .. ':' .. tostring(now)
    redis.call('ZADD', key, now, member)
    allowed   = 1
    remaining = remaining - 1
end

-- Step 4: Set TTL = window + 1 second buffer (auto-clean when window is idle)
redis.call('EXPIRE', key, tonumber(ARGV[2]) + 1)

-- Return {allowed (0 or 1), remaining slots}
return {allowed, remaining}
