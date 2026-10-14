package com.stocksmart.entity;

/**
 * Purchase order lifecycle (Day 4). No approval workflow.
 * Cancel never reverses stock already received.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    SUBMITTED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}
