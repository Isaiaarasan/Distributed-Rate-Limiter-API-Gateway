package com.ratelimiter.gateway.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Uniform API response envelope for all admin endpoints.
 *
 * Success: { success: true, message: "...", data: {...}, timestamp: "..." }
 * Error:   { success: false, error: "ERROR_CODE", message: "...", timestamp: "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String error,
        LocalDateTime timestamp
) {

    /**
     * Standard success response with data payload.
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data, null, LocalDateTime.now());
    }

    /**
     * Success response with a custom message (e.g., "Client created. Save your API key!").
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, LocalDateTime.now());
    }

    /**
     * Success response with no data payload.
     */
    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, null, LocalDateTime.now());
    }

    /**
     * Error response with an error code and message.
     */
    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, message, null, errorCode, LocalDateTime.now());
    }
}
