package com.ratelimiter.gateway.common.exception;

/**
 * Thrown when a requested entity (Client, ApiEndpoint, Policy) is not found in MySQL.
 * Results in HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " with id [" + id + "] was not found.");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
