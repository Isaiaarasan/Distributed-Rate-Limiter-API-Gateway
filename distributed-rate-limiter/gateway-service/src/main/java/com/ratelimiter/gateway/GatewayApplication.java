package com.ratelimiter.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the Distributed Rate Limiter & API Gateway.
 *
 * Architecture:
 *   Client → API Gateway (this service) → Rate Limiter (Redis) → Backend Service
 *
 * - Token Bucket algorithm: allows burst traffic with gradual token refill
 * - Sliding Window algorithm: precise per-window request counting
 * - Redis Lua scripts ensure atomic distributed state across multiple instances
 * - MySQL stores client/API/policy configuration (read rarely, cached aggressively)
 */
@SpringBootApplication
@EnableAsync
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
