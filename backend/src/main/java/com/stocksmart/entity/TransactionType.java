package com.stocksmart.entity;

/**
 * Stock movement types (Day 3).
 *
 * <p>Day 3 emits {@code OPENING_BALANCE} (first-ever receipt creating an
 * inventory row), {@code RECEIPT}, {@code ADJUSTMENT_IN}, {@code ADJUSTMENT_OUT},
 * {@code TRANSFER_IN} and {@code TRANSFER_OUT}. {@code ISSUE} is reserved for
 * Day 4 sales-order flows and is never written by Day 3 code.
 */
public enum TransactionType {
    OPENING_BALANCE,
    RECEIPT,
    ISSUE,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    TRANSFER_IN,
    TRANSFER_OUT
}
