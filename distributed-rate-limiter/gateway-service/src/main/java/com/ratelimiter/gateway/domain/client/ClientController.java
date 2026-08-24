package com.ratelimiter.gateway.domain.client;

import com.ratelimiter.gateway.common.dto.ApiResponse;
import com.ratelimiter.gateway.domain.client.dto.ClientResponse;
import com.ratelimiter.gateway.domain.client.dto.CreateClientRequest;
import com.ratelimiter.gateway.domain.client.dto.UpdateClientRequest;
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
 * Admin REST API for managing API clients.
 *
 * Base path: /api/admin/clients
 * This path is excluded from rate limiting (see RateLimitGatewayFilter.shouldSkip()).
 */
@RestController
@RequestMapping("/api/admin/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Manage API clients and their API keys")
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    @Operation(
        summary     = "Create a new client",
        description = "Creates a new API client and generates a unique API key. " +
                      "⚠️ The API key is shown ONCE in the response — save it securely!"
    )
    public Mono<ResponseEntity<ApiResponse<ClientResponse>>> createClient(
            @RequestBody @Valid CreateClientRequest request) {
        return clientService.createClient(request)
                .map(pair -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ApiResponse.success(
                                "Client created successfully! ⚠️ Save your API key — it will not be shown again.",
                                ClientService.toResponse(pair.client(), pair.rawApiKey()))));
    }

    @GetMapping
    @Operation(summary = "List all clients", description = "Returns all registered API clients.")
    public Mono<ResponseEntity<ApiResponse<List<ClientResponse>>>> getAllClients() {
        return clientService.findAll()
                .map(clients -> ResponseEntity.ok(ApiResponse.success(clients)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get client by ID")
    public Mono<ResponseEntity<ApiResponse<ClientResponse>>> getClient(@PathVariable Long id) {
        return clientService.findById(id)
                .map(client -> ResponseEntity.ok(
                        ApiResponse.success(ClientService.toResponse(client, null))));
    }

    @PutMapping("/{id}")
    @Operation(
        summary     = "Update a client",
        description = "Updates client name, description, or status. " +
                      "Changing status to INACTIVE or SUSPENDED immediately blocks their requests."
    )
    public Mono<ResponseEntity<ApiResponse<ClientResponse>>> updateClient(
            @PathVariable Long id,
            @RequestBody UpdateClientRequest request) {
        return clientService.updateClient(id, request)
                .map(updated -> ResponseEntity.ok(ApiResponse.success("Client updated.", updated)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a client", description = "Permanently deletes a client and all their rate limit policies.")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteClient(@PathVariable Long id) {
        return clientService.deleteClient(id)
                .then(Mono.just(ResponseEntity.ok(ApiResponse.success("Client deleted."))));
    }
}
