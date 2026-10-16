package com.stocksmart.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Day 5 KPI summary — core inventory metrics (no order data, so every
 * authenticated role may read it). Order KPIs live in the dedicated
 * purchase-order / sales-fulfillment summary endpoints.
 *
 * <p>Definitions (also documented in design.md):
 * <ul>
 *   <li>{@code totalProducts} / {@code activeProducts} — product master rows
 *       (all / active) — inactive rows are never silently dropped from the
 *       totals, both numbers are reported.</li>
 *   <li>{@code totalWarehouses} / {@code activeWarehouses} — same for warehouses.</li>
 *   <li>{@code totalInventoryUnits} — SUM(quantity_on_hand) across balances.</li>
 *   <li>{@code inventoryValue} — SUM(quantity_on_hand × product.cost_price),
 *       a reporting estimate at the current cost basis (not an accounting
 *       valuation).</li>
 *   <li>{@code lowStockCount} — balances with 0 &lt; qty &lt; product minimum
 *       stock level.</li>
 *   <li>{@code stockoutCount} — balances with qty = 0.</li>
 * </ul>
 * All inventory metrics honour the optional warehouse filter.
 */
public class DashboardSummaryResponse {

    private Long warehouseId;
    private long totalProducts;
    private long activeProducts;
    private long totalWarehouses;
    private long activeWarehouses;
    private BigDecimal totalInventoryUnits;
    private BigDecimal inventoryValue;
    private long lowStockCount;
    private long stockoutCount;
    private Instant generatedAt;

    public DashboardSummaryResponse() {
    }

    public DashboardSummaryResponse(Long warehouseId, long totalProducts, long activeProducts,
                                    long totalWarehouses, long activeWarehouses,
                                    BigDecimal totalInventoryUnits, BigDecimal inventoryValue,
                                    long lowStockCount, long stockoutCount, Instant generatedAt) {
        this.warehouseId = warehouseId;
        this.totalProducts = totalProducts;
        this.activeProducts = activeProducts;
        this.totalWarehouses = totalWarehouses;
        this.activeWarehouses = activeWarehouses;
        this.totalInventoryUnits = totalInventoryUnits;
        this.inventoryValue = inventoryValue;
        this.lowStockCount = lowStockCount;
        this.stockoutCount = stockoutCount;
        this.generatedAt = generatedAt;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public long getActiveProducts() {
        return activeProducts;
    }

    public long getTotalWarehouses() {
        return totalWarehouses;
    }

    public long getActiveWarehouses() {
        return activeWarehouses;
    }

    public BigDecimal getTotalInventoryUnits() {
        return totalInventoryUnits;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }

    public long getStockoutCount() {
        return stockoutCount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
