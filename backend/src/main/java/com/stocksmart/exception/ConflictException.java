package com.stocksmart.exception;

/** Thrown when a request conflicts with existing data (duplicate codes, referenced deletes) → 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
