package com.stocksmart.entity;

/**
 * Sales order lifecycle (Day 4). No reservation on confirm — stock is
 * checked and issued at fulfillment time only.
 */
public enum SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    CANCELLED
}
