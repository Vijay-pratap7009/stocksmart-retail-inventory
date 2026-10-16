package com.stocksmart.dto;

import com.stocksmart.entity.PurchaseOrderStatus;
import java.time.Instant;
import java.util.Map;

/**
 * Purchase-order KPI summary (Day 5).
 *
 * <p>{@code byStatus} counts every order once per lifecycle state (zero for
 * absent states). {@code pendingCount} = SUBMITTED + PARTIALLY_RECEIVED
 * (open, awaiting or partially receiving goods). DRAFT orders are pending
 * work but not yet committed — reported separately via {@code byStatus}.
 * Optionally scoped to the destination warehouse.
 */
public class PurchaseOrderSummaryResponse {

    private Long warehouseId;
    private Map<PurchaseOrderStatus, Long> byStatus;
    private long totalOrders;
    private long pendingCount;
    private Instant generatedAt;

    public PurchaseOrderSummaryResponse() {
    }

    public PurchaseOrderSummaryResponse(Long warehouseId, Map<PurchaseOrderStatus, Long> byStatus,
                                        long totalOrders, long pendingCount, Instant generatedAt) {
        this.warehouseId = warehouseId;
        this.byStatus = byStatus;
        this.totalOrders = totalOrders;
        this.pendingCount = pendingCount;
        this.generatedAt = generatedAt;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Map<PurchaseOrderStatus, Long> getByStatus() {
        return byStatus;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
