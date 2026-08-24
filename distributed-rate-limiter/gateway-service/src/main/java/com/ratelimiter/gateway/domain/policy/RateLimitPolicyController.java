package com.ratelimiter.gateway.domain.policy;

import com.ratelimiter.gateway.common.dto.ApiResponse;
import com.ratelimiter.gateway.domain.policy.dto.CreatePolicyRequest;
import com.ratelimiter.gateway.domain.policy.dto.PolicyResponse;
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
 * Admin REST API for managing rate limit policies.
 *
 * This is the most important admin endpoint.
 * PUT /api/admin/rate-limits/{id} changes limits LIVE without restart.
 *
 * Base path: /api/admin/rate-limits
 */
@RestController
@RequestMapping("/api/admin/rate-limits")
@RequiredArgsConstructor
@Tag(name = "Rate Limit Policies", description = "Configure per-client per-API rate limit rules")
public class RateLimitPolicyController {

    private final RateLimitPolicyService policyService;

    @PostMapping
    @Operation(
        summary     = "Create a rate limit policy",
        description = """
            Creates a rate limit policy linking a Client to an API endpoint.

            **Token Bucket**: provide `bucketCapacity` and `refillRate` (tokens/second).
            Example: capacity=100, refillRate=2.0 → max burst of 100, refills 2 tokens/sec.

            **Sliding Window**: provide `maxRequests` and `windowSeconds`.
            Example: maxRequests=50, windowSeconds=60 → max 50 requests per 60-second window.
            """
    )
    public Mono<ResponseEntity<ApiResponse<PolicyResponse>>> createPolicy(
            @RequestBody @Valid CreatePolicyRequest request) {
        return policyService.createPolicy(request)
                .map(policy -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ApiResponse.success("Rate limit policy created.", policy)));
    }

    @GetMapping
    @Operation(summary = "List all rate limit policies")
    public Mono<ResponseEntity<ApiResponse<List<PolicyResponse>>>> getAllPolicies() {
        return policyService.findAll()
                .map(policies -> ResponseEntity.ok(ApiResponse.success(policies)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a rate limit policy by ID")
    public Mono<ResponseEntity<ApiResponse<PolicyResponse>>> getPolicy(@PathVariable Long id) {
        return policyService.findById(id)
                .map(policy -> ResponseEntity.ok(ApiResponse.success(policy)));
    }

    @PutMapping("/{id}")
    @Operation(
        summary     = "Update a rate limit policy (LIVE)",
        description = "Updates the rate limit policy. Changes take effect on the NEXT request " +
                      "(within 30 seconds due to policy cache TTL). No server restart needed."
    )
    public Mono<ResponseEntity<ApiResponse<PolicyResponse>>> updatePolicy(
            @PathVariable Long id,
            @RequestBody CreatePolicyRequest request) {
        return policyService.updatePolicy(id, request)
                .map(updated -> ResponseEntity.ok(ApiResponse.success(
                        "Policy updated. Changes will propagate within 30 seconds.", updated)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a rate limit policy", description = "After deletion, the client+API pair is no longer rate-limited.")
    public Mono<ResponseEntity<ApiResponse<Void>>> deletePolicy(@PathVariable Long id) {
        return policyService.deletePolicy(id)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success("Rate limit policy deleted."))));
    }
}
