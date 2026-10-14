package com.stocksmart.controller;

import com.stocksmart.dto.AdjustmentRequest;
import com.stocksmart.dto.InventoryResponse;
import com.stocksmart.dto.ReceiptRequest;
import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.dto.TransferRequest;
import com.stocksmart.dto.TransferResponse;
import com.stocksmart.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inventory endpoints (Day 3).
 * Reads: any authenticated role. Stock-changing operations (receipts,
 * adjustments, transfers): ADMIN, INVENTORY_MANAGER, WAREHOUSE_STAFF.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Warehouse stock balances and movements (Day 3)")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private static final String STOCK_WRITER =
            "hasAnyRole('ADMIN','INVENTORY_MANAGER','WAREHOUSE_STAFF')";

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @Operation(summary = "List inventory balances with pagination and filters")
    public ResponseEntity<Page<InventoryResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(inventoryService.list(search, productId, warehouseId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one inventory balance by id")
    public ResponseEntity<InventoryResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(inventoryService.get(id));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "View stock across warehouses for a product")
    public ResponseEntity<List<InventoryResponse>> byProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.byProduct(productId));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "View stock held at a warehouse")
    public ResponseEntity<List<InventoryResponse>> byWarehouse(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(inventoryService.byWarehouse(warehouseId));
    }

    @PostMapping("/receipts")
    @PreAuthorize(STOCK_WRITER)
    @Operation(summary = "Record a manual stock receipt (no purchase order required)")
    public ResponseEntity<StockTransactionResponse> receipt(
            @Valid @RequestBody ReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.receipt(request));
    }

    @PostMapping("/adjustments")
    @PreAuthorize(STOCK_WRITER)
    @Operation(summary = "Apply an authorized stock adjustment (reason required)")
    public ResponseEntity<StockTransactionResponse> adjust(
            @Valid @RequestBody AdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.adjust(request));
    }

    @PostMapping("/transfers")
    @PreAuthorize(STOCK_WRITER)
    @Operation(summary = "Transfer stock between warehouses (atomic, paired legs)")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.transfer(request));
    }
}
