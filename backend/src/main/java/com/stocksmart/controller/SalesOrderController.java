package com.stocksmart.controller;

import com.stocksmart.dto.FulfillmentRequest;
import com.stocksmart.dto.FulfillmentResponse;
import com.stocksmart.dto.SalesOrderRequest;
import com.stocksmart.dto.SalesOrderResponse;
import com.stocksmart.entity.SalesOrderStatus;
import com.stocksmart.service.SalesOrderService;
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
 * Sales order endpoints (Day 4).
 * Reads: ADMIN, INVENTORY_MANAGER, SALES_USER, WAREHOUSE_STAFF, MANAGEMENT.
 * Order writes: ADMIN + SALES_USER.
 * Fulfillments: ADMIN + SALES_USER + INVENTORY_MANAGER + WAREHOUSE_STAFF.
 */
@RestController
@RequestMapping("/api/v1/sales-orders")
@Tag(name = "Sales orders", description = "Sales lifecycle + fulfillment (Day 4)")
@SecurityRequirement(name = "bearerAuth")
public class SalesOrderController {

    private static final String SO_READER =
            "hasAnyRole('ADMIN','INVENTORY_MANAGER','SALES_USER','WAREHOUSE_STAFF','MANAGEMENT')";
    private static final String SO_WRITER = "hasAnyRole('ADMIN','SALES_USER')";
    private static final String SO_FULFILLER =
            "hasAnyRole('ADMIN','SALES_USER','INVENTORY_MANAGER','WAREHOUSE_STAFF')";

    private final SalesOrderService orderService;

    public SalesOrderController(SalesOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize(SO_READER)
    @Operation(summary = "List/search sales orders with pagination")
    public ResponseEntity<Page<SalesOrderResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SalesOrderStatus status,
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(orderService.list(search, status, warehouseId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize(SO_READER)
    @Operation(summary = "View a sales order with lines")
    public ResponseEntity<SalesOrderResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id));
    }

    @GetMapping("/{id}/fulfillments")
    @PreAuthorize(SO_READER)
    @Operation(summary = "Fulfillment history for a sales order")
    public ResponseEntity<List<FulfillmentResponse>> fulfillments(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.fulfillments(id));
    }

    @PostMapping
    @PreAuthorize(SO_WRITER)
    @Operation(summary = "Create a draft sales order (ADMIN, SALES_USER)")
    public ResponseEntity<SalesOrderResponse> create(
            @Valid @RequestBody SalesOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SO_WRITER)
    @Operation(summary = "Update a draft sales order (ADMIN, SALES_USER)")
    public ResponseEntity<SalesOrderResponse> update(
            @PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return ResponseEntity.ok(orderService.update(id, request));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(SO_WRITER)
    @Operation(summary = "Confirm a draft sales order; does not reserve stock (ADMIN, SALES_USER)")
    public ResponseEntity<SalesOrderResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.confirm(id));
    }

    @PostMapping("/{id}/fulfillments")
    @PreAuthorize(SO_FULFILLER)
    @Operation(summary = "Fulfill quantities; decreases inventory atomically")
    public ResponseEntity<FulfillmentResponse> fulfill(
            @PathVariable Long id, @Valid @RequestBody FulfillmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.fulfill(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SO_WRITER)
    @Operation(summary = "Cancel an open sales order (ADMIN, SALES_USER)")
    public ResponseEntity<SalesOrderResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }
}
