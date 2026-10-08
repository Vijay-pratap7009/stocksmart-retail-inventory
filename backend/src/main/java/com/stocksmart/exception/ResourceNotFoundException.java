package com.stocksmart.exception;

/** Thrown when a requested resource does not exist → rendered as 404 JSON. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
