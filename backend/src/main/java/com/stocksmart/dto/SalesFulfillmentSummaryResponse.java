package com.stocksmart.dto;

import com.stocksmart.entity.SalesOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Sales fulfillment KPI summary (Day 5).
 *
 * <p>{@code byStatus} counts every order once per lifecycle state (zero for
 * absent states). {@code pendingCount} = CONFIRMED + PARTIALLY_FULFILLED
 * (orders with quantities outstanding). {@code remainingToFulfillUnits} =
 * SUM(ordered − fulfilled) over the lines of those same orders — each line
 * counted exactly once. Optionally scoped to the fulfilling warehouse.
 */
public class SalesFulfillmentSummaryResponse {

    private Long warehouseId;
    private Map<SalesOrderStatus, Long> byStatus;
    private long totalOrders;
    private long pendingCount;
    private BigDecimal remainingToFulfillUnits;
    private Instant generatedAt;

    public SalesFulfillmentSummaryResponse() {
    }

    public SalesFulfillmentSummaryResponse(Long warehouseId, Map<SalesOrderStatus, Long> byStatus,
                                           long totalOrders, long pendingCount,
                                           BigDecimal remainingToFulfillUnits,
                                           Instant generatedAt) {
        this.warehouseId = warehouseId;
        this.byStatus = byStatus;
        this.totalOrders = totalOrders;
        this.pendingCount = pendingCount;
        this.remainingToFulfillUnits = remainingToFulfillUnits;
        this.generatedAt = generatedAt;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Map<SalesOrderStatus, Long> getByStatus() {
        return byStatus;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public BigDecimal getRemainingToFulfillUnits() {
        return remainingToFulfillUnits;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
