package com.legacybridge.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic API response wrapper for the LegacyBridge REST API.
 * Wraps all API responses with a consistent structure including
 * success status, message, and data payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private boolean success;
    private String message;
    private Object data;

    public ApiResponse() {
    }

    public ApiResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ApiResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * Factory method for successful responses with data.
     */
    public static ApiResponse ok(String message, Object data) {
        return new ApiResponse(true, message, data);
    }

    /**
     * Factory method for successful responses without data.
     */
    public static ApiResponse ok(String message) {
        return new ApiResponse(true, message);
    }

    /**
     * Factory method for error responses.
     */
    public static ApiResponse error(String message) {
        return new ApiResponse(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
