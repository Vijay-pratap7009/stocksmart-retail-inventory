package com.stocksmart.service;

import com.stocksmart.dto.DashboardSummaryResponse;
import com.stocksmart.dto.LowStockItemResponse;
import com.stocksmart.dto.PurchaseOrderSummaryResponse;
import com.stocksmart.dto.SalesFulfillmentSummaryResponse;
import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.dto.StockoutItemResponse;
import com.stocksmart.entity.PurchaseOrderStatus;
import com.stocksmart.entity.SalesOrderStatus;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.DashboardMapper;
import com.stocksmart.repository.InventoryRepository;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.PurchaseOrderRepository;
import com.stocksmart.repository.SalesOrderItemRepository;
import com.stocksmart.repository.SalesOrderRepository;
import com.stocksmart.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day 5 Inventory KPI dashboard service.
 *
 * <p>All metrics are computed from persisted data with database-side
 * aggregation queries (no full-table loads into the application and no N+1
 * patterns). Exact KPI definitions are documented on the response DTOs and
 * in design.md. Read-only — dashboard access never changes stock.
 */
@Service
public class DashboardService {

    /** Maximum number of rows returned by the recent-transactions feed. */
    private static final int MAX_RECENT_TRANSACTIONS = 50;

    /** PO states counted as "pending" (committed, awaiting goods). */
    private static final Set<PurchaseOrderStatus> PENDING_PO_STATUSES =
            EnumSet.of(PurchaseOrderStatus.SUBMITTED, PurchaseOrderStatus.PARTIALLY_RECEIVED);

    /** SO states counted as "pending fulfillment" (quantities outstanding). */
    private static final Set<SalesOrderStatus> PENDING_SO_STATUSES =
            EnumSet.of(SalesOrderStatus.CONFIRMED, SalesOrderStatus.PARTIALLY_FULFILLED);

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderItemRepository salesOrderItemRepository;
    private final StockTransactionService stockTransactionService;
    private final DashboardMapper dashboardMapper;

    public DashboardService(InventoryRepository inventoryRepository,
                            ProductRepository productRepository,
                            WarehouseRepository warehouseRepository,
                            PurchaseOrderRepository purchaseOrderRepository,
                            SalesOrderRepository salesOrderRepository,
                            SalesOrderItemRepository salesOrderItemRepository,
                            StockTransactionService stockTransactionService,
                            DashboardMapper dashboardMapper) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderItemRepository = salesOrderItemRepository;
        this.stockTransactionService = stockTransactionService;
        this.dashboardMapper = dashboardMapper;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long warehouseId) {
        requireWarehouseIfPresent(warehouseId);
        BigDecimal units = inventoryRepository.sumQuantityOnHand(warehouseId);
        BigDecimal value = inventoryRepository.sumInventoryValue(warehouseId)
                .setScale(2, RoundingMode.HALF_UP);
        return new DashboardSummaryResponse(
                warehouseId,
                productRepository.count(),
                productRepository.countByActiveTrue(),
                warehouseRepository.count(),
                warehouseRepository.countByActiveTrue(),
                units,
                value,
                inventoryRepository.countLowStock(warehouseId),
                inventoryRepository.countStockouts(warehouseId),
                Instant.now());
    }

    @Transactional(readOnly = true)
    public Page<LowStockItemResponse> lowStock(Long warehouseId, Pageable pageable) {
        requireWarehouseIfPresent(warehouseId);
        return inventoryRepository.findLowStock(warehouseId, pageable)
                .map(dashboardMapper::toLowStockItem);
    }

    @Transactional(readOnly = true)
    public Page<StockoutItemResponse> stockouts(Long warehouseId, Pageable pageable) {
        requireWarehouseIfPresent(warehouseId);
        return inventoryRepository.findStockouts(warehouseId, pageable)
                .map(dashboardMapper::toStockoutItem);
    }

    @Transactional(readOnly = true)
    public List<StockTransactionResponse> recentTransactions(Long productId, Long warehouseId,
                                                             int limit) {
        requireWarehouseIfPresent(warehouseId);
        int capped = Math.max(1, Math.min(limit, MAX_RECENT_TRANSACTIONS));
        Pageable pageable = PageRequest.of(0, capped, Sort.by(Sort.Direction.DESC, "createdAt"));
        return stockTransactionService
                .search(productId, warehouseId, null, null, null, null, pageable)
                .getContent();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderSummaryResponse purchaseOrderSummary(Long warehouseId) {
        requireWarehouseIfPresent(warehouseId);
        Map<PurchaseOrderStatus, Long> byStatus = zeroFilled(PurchaseOrderStatus.values());
        long total = 0;
        for (Object[] row : purchaseOrderRepository.countByStatus(warehouseId)) {
            PurchaseOrderStatus status = (PurchaseOrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
            total += count;
        }
        long pending = PENDING_PO_STATUSES.stream().mapToLong(byStatus::get).sum();
        return new PurchaseOrderSummaryResponse(warehouseId, byStatus, total, pending,
                Instant.now());
    }

    @Transactional(readOnly = true)
    public SalesFulfillmentSummaryResponse salesFulfillmentSummary(Long warehouseId) {
        requireWarehouseIfPresent(warehouseId);
        Map<SalesOrderStatus, Long> byStatus = zeroFilled(SalesOrderStatus.values());
        long total = 0;
        for (Object[] row : salesOrderRepository.countByStatus(warehouseId)) {
            SalesOrderStatus status = (SalesOrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
            total += count;
        }
        long pending = PENDING_SO_STATUSES.stream().mapToLong(byStatus::get).sum();
        BigDecimal remaining = salesOrderItemRepository
                .sumRemainingQuantity(PENDING_SO_STATUSES, warehouseId)
                .setScale(4, RoundingMode.HALF_UP);
        return new SalesFulfillmentSummaryResponse(warehouseId, byStatus, total, pending,
                remaining, Instant.now());
    }

    private void requireWarehouseIfPresent(Long warehouseId) {
        if (warehouseId != null && !warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse " + warehouseId + " not found");
        }
    }

    private static <E extends Enum<E>> Map<E, Long> zeroFilled(E[] values) {
        Map<E, Long> map = new LinkedHashMap<>();
        for (E value : values) {
            map.put(value, 0L);
        }
        return map;
    }
}
