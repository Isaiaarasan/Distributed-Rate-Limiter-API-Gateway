package com.ratelimiter.product;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Mock Product Service REST Controller.
 * Simulates a real product microservice behind the API Gateway.
 *
 * This service runs on port 8081 and receives requests forwarded
 * by the gateway AFTER rate limiting has been applied.
 *
 * Notice the X-Client-Id header injected by the gateway.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final List<Map<String, Object>> PRODUCTS = List.of(
        Map.of("id", 1, "name", "Laptop Pro X", "price", 1299.99, "category", "Electronics", "stock", 50),
        Map.of("id", 2, "name", "Wireless Headphones", "price", 149.99, "category", "Audio", "stock", 200),
        Map.of("id", 3, "name", "Ergonomic Chair", "price", 449.99, "category", "Furniture", "stock", 25),
        Map.of("id", 4, "name", "Standing Desk", "price", 799.99, "category", "Furniture", "stock", 15),
        Map.of("id", 5, "name", "USB-C Hub", "price", 49.99, "category", "Accessories", "stock", 500)
    );

    @GetMapping
    public Map<String, Object> getAllProducts(
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        return Map.of(
            "success",    true,
            "service",    "product-service",
            "clientId",   clientId,
            "data",       PRODUCTS,
            "count",      PRODUCTS.size(),
            "timestamp",  LocalDateTime.now().toString()
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> getProductById(
            @PathVariable int id,
            @RequestHeader(value = "X-Client-Id", defaultValue = "unknown") String clientId) {
        if (id < 1 || id > PRODUCTS.size()) {
            return Map.of("success", false, "error", "PRODUCT_NOT_FOUND",
                          "message", "Product with id " + id + " not found");
        }
        return Map.of(
            "success",   true,
            "service",   "product-service",
            "clientId",  clientId,
            "data",      PRODUCTS.get(id - 1),
            "timestamp", LocalDateTime.now().toString()
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "product-service", "port", 8081);
    }
}
