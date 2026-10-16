package com.stocksmart.entity;

/**
 * Outcome of processing one RFID read event (Day 5).
 *
 * <p>A read is informational only — none of these statuses changes stock.
 * Stock movements require an explicit, authorized workflow (receipt,
 * adjustment, transfer, order receipt/fulfillment).
 */
public enum RfidReadStatus {
    /** Tag exists, is active and resolves to a product. */
    RESOLVED,
    /** Tag exists but is deactivated; the mapping is kept for history. */
    INACTIVE_TAG,
    /** No tag registered for the read EPC. */
    UNKNOWN_TAG
}
