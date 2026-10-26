package com.omni.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard JSON envelope returned by every service: {code, message, data, timestamp}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, Instant timestamp) {

    public static final String SUCCESS = "SUCCESS";

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS, "OK", data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(SUCCESS, message, data, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, T details) {
        return new ApiResponse<>(code, message, details, Instant.now());
    }
}
