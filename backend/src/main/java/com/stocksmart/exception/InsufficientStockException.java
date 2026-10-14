package com.stocksmart.exception;

/** Thrown when a stock decrease exceeds the quantity on hand → 409 with a clear message. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
