package com.stocksmart.controller;

import com.stocksmart.dto.PurchaseOrderRequest;
import com.stocksmart.dto.PurchaseOrderResponse;
import com.stocksmart.dto.PurchaseReceiptRequest;
import com.stocksmart.dto.PurchaseReceiptResponse;
import com.stocksmart.entity.PurchaseOrderStatus;
import com.stocksmart.service.PurchaseOrderService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Purchase order endpoints (Day 4).
 * Reads: ADMIN, INVENTORY_MANAGER, PROCUREMENT_USER, WAREHOUSE_STAFF, MANAGEMENT.
 * Order writes: ADMIN + PROCUREMENT_USER.
 * Goods receipts: ADMIN + PROCUREMENT_USER + INVENTORY_MANAGER + WAREHOUSE_STAFF.
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "Purchase orders", description = "Procurement lifecycle + goods receipt (Day 4)")
@SecurityRequirement(name = "bearerAuth")
public class PurchaseOrderController {

    private static final String PO_READER =
            "hasAnyRole('ADMIN','INVENTORY_MANAGER','PROCUREMENT_USER','WAREHOUSE_STAFF','MANAGEMENT')";
    private static final String PO_WRITER = "hasAnyRole('ADMIN','PROCUREMENT_USER')";
    private static final String PO_RECEIVER =
            "hasAnyRole('ADMIN','PROCUREMENT_USER','INVENTORY_MANAGER','WAREHOUSE_STAFF')";

    private final PurchaseOrderService orderService;

    public PurchaseOrderController(PurchaseOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize(PO_READER)
    @Operation(summary = "List/search purchase orders with pagination")
    public ResponseEntity<Page<PurchaseOrderResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long supplierId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(orderService.list(search, status, supplierId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PO_READER)
    @Operation(summary = "View a purchase order with lines")
    public ResponseEntity<PurchaseOrderResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id));
    }

    @GetMapping("/{id}/receipts")
    @PreAuthorize(PO_READER)
    @Operation(summary = "Receipt history for a purchase order")
    public ResponseEntity<List<PurchaseReceiptResponse>> receipts(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.receipts(id));
    }

    @PostMapping
    @PreAuthorize(PO_WRITER)
    @Operation(summary = "Create a draft purchase order (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<PurchaseOrderResponse> create(
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PO_WRITER)
    @Operation(summary = "Update a draft purchase order (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<PurchaseOrderResponse> update(
            @PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.ok(orderService.update(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(PO_WRITER)
    @Operation(summary = "Submit a draft purchase order (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<PurchaseOrderResponse> submit(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.submit(id));
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize(PO_RECEIVER)
    @Operation(summary = "Record a goods receipt; updates inventory atomically")
    public ResponseEntity<PurchaseReceiptResponse> receive(
            @PathVariable Long id, @Valid @RequestBody PurchaseReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.receive(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(PO_WRITER)
    @Operation(summary = "Cancel an open purchase order; received stock is kept (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<PurchaseOrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}
