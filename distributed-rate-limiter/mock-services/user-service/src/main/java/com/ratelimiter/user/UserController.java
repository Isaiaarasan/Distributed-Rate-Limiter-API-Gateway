package com.ratelimiter.user;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mock User Service REST Controller.
 * Simulates a real user microservice behind the API Gateway.
 * Runs on port 8083.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final List<Map<String, Object>> USERS = List.of(
        Map.of("id", 1, "username", "alice_dev", "email", "alice@example.com",
               "role", "ADMIN", "plan", "enterprise", "active", true),
        Map.of("id", 2, "username", "bob_user", "email", "bob@example.com",
               "role", "USER", "plan", "standard", "active", true),
        Map.of("id", 3, "username", "charlie_api", "email", "charlie@example.com",
               "role", "USER", "plan", "basic", "active", false)
    );

    @GetMapping
    public Map<String, Object> getAllUsers(
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        return Map.of(
            "success",   true,
            "service",   "user-service",
            "clientId",  clientId,
            "data",      USERS,
            "count",     USERS.size(),
            "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getUserById(
            @PathVariable int id,
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        if (id < 1 || id > USERS.size()) {
            return Map.of("success", false, "error", "USER_NOT_FOUND",
                          "message", "User with id " + id + " not found");
        }
        return Map.of(
            "success",   true,
            "service",   "user-service",
            "clientId",  clientId,
            "data",      USERS.get(id - 1),
            "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "user-service", "port", 8083);
    }
}
