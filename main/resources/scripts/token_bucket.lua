--[[
  Token Bucket Rate Limiter - Redis Lua Script
  ─────────────────────────────────────────────
  Executes atomically inside Redis. No other command can interleave.

  KEYS[1]  = Redis hash key (e.g., "rl:tb:mobile-app::api:products")
  ARGV[1]  = capacity      (max tokens the bucket can hold)
  ARGV[2]  = refillRate    (tokens added per second, can be a float)
  ARGV[3]  = now           (current time in milliseconds)
  ARGV[4]  = requested     (tokens requested, always "1")

  Returns: {allowed, remaining}
    allowed   → 1 = allow the request, 0 = deny with 429
    remaining → tokens left in the bucket after this decision
--]]

local key        = KEYS[1]
local capacity   = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now        = tonumber(ARGV[3])
local requested  = tonumber(ARGV[4])

-- Read current state from Redis hash
local bucket    = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens    = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

-- Initialize bucket on first request
if tokens == nil then
    tokens    = capacity
    lastRefill = now
end

-- Calculate how many tokens to add based on elapsed time
-- elapsedSeconds = (now - lastRefill) / 1000
-- newTokens = elapsedSeconds * refillRate
local elapsedMs  = math.max(0, now - lastRefill)
local newTokens  = (elapsedMs / 1000.0) * refillRate

-- Refill the bucket, capped at capacity
tokens    = math.min(capacity, tokens + newTokens)
lastRefill = now

local allowed   = 0
local remaining = math.floor(tokens)

-- Check if we can consume a token
if tokens >= requested then
    tokens    = tokens - requested
    allowed   = 1
    remaining = math.floor(tokens)
end

-- Persist the new state
-- TTL = time to fully refill the bucket from 0 + 60s buffer
local ttl = math.ceil((capacity / refillRate) + 60)
redis.call('HMSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(lastRefill))
redis.call('EXPIRE', key, ttl)

-- Return {allowed (0 or 1), remaining tokens}
return {allowed, remaining}
