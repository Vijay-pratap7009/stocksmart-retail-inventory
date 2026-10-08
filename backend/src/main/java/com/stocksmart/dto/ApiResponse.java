package com.stocksmart.dto;

/** Lightweight wrapper for simple verification endpoints. */
public class ApiResponse {

    private String status;
    private String message;
    private Object data;

    public ApiResponse() {
    }

    public ApiResponse(String status, String message) {
        this(status, message, null);
    }

    public ApiResponse(String status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse ok(String message) {
        return new ApiResponse("OK", message);
    }

    public static ApiResponse ok(String message, Object data) {
        return new ApiResponse("OK", message, data);
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
