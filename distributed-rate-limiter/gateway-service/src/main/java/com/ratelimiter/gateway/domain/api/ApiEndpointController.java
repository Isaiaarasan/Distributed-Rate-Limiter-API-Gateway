package com.ratelimiter.gateway.domain.api;

import com.ratelimiter.gateway.common.dto.ApiResponse;
import com.ratelimiter.gateway.domain.api.dto.ApiEndpointResponse;
import com.ratelimiter.gateway.domain.api.dto.CreateApiEndpointRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Admin REST API for managing protected API endpoints.
 *
 * Base path: /api/admin/apis
 */
@RestController
@RequestMapping("/api/admin/apis")
@RequiredArgsConstructor
@Tag(name = "API Endpoints", description = "Register and manage protected API endpoints")
public class ApiEndpointController {

    private final ApiEndpointService apiEndpointService;

    @PostMapping
    @Operation(
        summary     = "Register a new API endpoint",
        description = "Registers an API route that can be rate-limited. " +
                      "After registering, create a rate limit policy to start enforcing limits."
    )
    public Mono<ResponseEntity<ApiResponse<ApiEndpointResponse>>> createEndpoint(
            @RequestBody @Valid CreateApiEndpointRequest request) {
        return apiEndpointService.createEndpoint(request)
                .map(endpoint -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ApiResponse.success("API endpoint registered.", endpoint)));
    }

    @GetMapping
    @Operation(summary = "List all API endpoints")
    public Mono<ResponseEntity<ApiResponse<List<ApiEndpointResponse>>>> getAllEndpoints() {
        return apiEndpointService.findAll()
                .map(endpoints -> ResponseEntity.ok(ApiResponse.success(endpoints)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get API endpoint by ID")
    public Mono<ResponseEntity<ApiResponse<ApiEndpointResponse>>> getEndpoint(@PathVariable Long id) {
        return apiEndpointService.findById(id)
                .map(ApiEndpointService::toResponse)
                .map(endpoint -> ResponseEntity.ok(ApiResponse.success(endpoint)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an API endpoint")
    public Mono<ResponseEntity<ApiResponse<ApiEndpointResponse>>> updateEndpoint(
            @PathVariable Long id,
            @RequestBody CreateApiEndpointRequest request) {
        return apiEndpointService.updateEndpoint(id, request)
                .map(updated -> ResponseEntity.ok(ApiResponse.success("API endpoint updated.", updated)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an API endpoint", description = "Deletes the endpoint and all associated rate limit policies.")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteEndpoint(@PathVariable Long id) {
        return apiEndpointService.deleteEndpoint(id)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success("API endpoint deleted."))));
    }
}
