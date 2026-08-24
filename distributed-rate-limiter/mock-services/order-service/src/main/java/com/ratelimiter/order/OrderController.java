package com.ratelimiter.order;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Mock Order Service REST Controller.
 * Simulates a real order microservice behind the API Gateway.
 * Runs on port 8082.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // In-memory order store for demo
    private static final List<Map<String, Object>> ORDERS = new ArrayList<>(List.of(
        Map.of("id", "ORD-001", "clientId", "mobile-app", "product", "Laptop Pro X",
               "quantity", 1, "total", 1299.99, "status", "DELIVERED",
               "createdAt", "2024-01-15T10:30:00"),
        Map.of("id", "ORD-002", "clientId", "web-app", "product", "Wireless Headphones",
               "quantity", 2, "total", 299.98, "status", "PROCESSING",
               "createdAt", "2024-01-16T14:22:00")
    ));

    @GetMapping
    public Map<String, Object> getAllOrders(
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        return Map.of(
            "success",   true,
            "service",   "order-service",
            "clientId",  clientId,
            "data",      ORDERS,
            "count",     ORDERS.size(),
            "timestamp", LocalDateTime.now().toString()
        );
    }

    @PostMapping
    public Map<String, Object> createOrder(
            @RequestBody Map<String, Object> orderRequest,
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        String orderId = "ORD-" + String.format("%03d", ORDERS.size() + 1);
        Map<String, Object> newOrder = new LinkedHashMap<>();
        newOrder.put("id", orderId);
        newOrder.put("clientId", clientId);
        newOrder.putAll(orderRequest);
        newOrder.put("status", "PENDING");
        newOrder.put("createdAt", LocalDateTime.now().toString());
        ORDERS.add(newOrder);

        return Map.of(
            "success",   true,
            "service",   "order-service",
            "message",   "Order created successfully",
            "data",      newOrder,
            "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getOrderById(
            @PathVariable String id,
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        return ORDERS.stream()
            .filter(o -> id.equals(o.get("id")))
            .findFirst()
            .map(order -> (Map<String, Object>) Map.of(
                "success", true, "service", "order-service",
                "clientId", clientId, "data", order,
                "timestamp", LocalDateTime.now().toString()))
            .orElse(Map.of("success", false, "error", "ORDER_NOT_FOUND",
                           "message", "Order " + id + " not found"));
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "order-service", "port", 8082);
    }
}
