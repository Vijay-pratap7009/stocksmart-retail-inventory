package com.stocksmart.controller;

import com.stocksmart.dto.DashboardSummaryResponse;
import com.stocksmart.dto.LowStockItemResponse;
import com.stocksmart.dto.PurchaseOrderSummaryResponse;
import com.stocksmart.dto.SalesFulfillmentSummaryResponse;
import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.dto.StockoutItemResponse;
import com.stocksmart.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory KPI dashboard endpoints (Day 5) — real persisted data only.
 *
 * <p>Core inventory KPIs (summary, low stock, stockouts, recent movements)
 * are readable by any authenticated role (all roles can read inventory).
 * Purchase-order and sales-fulfillment summaries mirror the Day 4 read
 * matrices — a role without access to a side gets 403 there.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Inventory KPI dashboard (Day 5)")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Core KPI summary (products, warehouses, units, value, low stock, stockouts)")
    public ResponseEntity<DashboardSummaryResponse> summary(
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(dashboardService.summary(warehouseId));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Low-stock balances (0 < quantity < product minimum stock level), paged")
    public ResponseEntity<Page<LowStockItemResponse>> lowStock(
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(dashboardService.lowStock(warehouseId, pageable));
    }

    @GetMapping("/stockouts")
    @Operation(summary = "Stockout balances (quantity = 0), paged")
    public ResponseEntity<Page<StockoutItemResponse>> stockouts(
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(dashboardService.stockouts(warehouseId, pageable));
    }

    @GetMapping("/recent-transactions")
    @Operation(summary = "Most recent stock movements (limit 1-50, default 10), newest first")
    public ResponseEntity<List<StockTransactionResponse>> recentTransactions(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
                dashboardService.recentTransactions(productId, warehouseId, limit));
    }

    @GetMapping("/purchase-orders")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','PROCUREMENT_USER','WAREHOUSE_STAFF','MANAGEMENT')")
    @Operation(summary = "Purchase-order counts per status + pending count (no sales roles)")
    public ResponseEntity<PurchaseOrderSummaryResponse> purchaseOrders(
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(dashboardService.purchaseOrderSummary(warehouseId));
    }

    @GetMapping("/sales-fulfillment")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','SALES_USER','WAREHOUSE_STAFF','MANAGEMENT')")
    @Operation(summary = "Sales fulfillment counts per status + remaining units (no procurement role)")
    public ResponseEntity<SalesFulfillmentSummaryResponse> salesFulfillment(
            @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(dashboardService.salesFulfillmentSummary(warehouseId));
    }
}
