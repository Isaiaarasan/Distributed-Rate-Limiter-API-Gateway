package com.ratelimiter.gateway.domain.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateApiEndpointRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "path is required")
        @Pattern(regexp = "^/.*", message = "path must start with /")
        String path,

        @NotBlank(message = "httpMethod is required")
        @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|ANY",
                 message = "httpMethod must be one of: GET, POST, PUT, DELETE, PATCH, ANY")
        String httpMethod,

        String serviceName,
        String targetUrl
) {}
