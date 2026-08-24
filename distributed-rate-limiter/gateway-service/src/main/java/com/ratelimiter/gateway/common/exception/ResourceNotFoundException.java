package com.ratelimiter.gateway.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " with id [" + id + "] was not found.");
    }
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
