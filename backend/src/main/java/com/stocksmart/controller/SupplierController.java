package com.stocksmart.controller;

import com.stocksmart.dto.SupplierRequest;
import com.stocksmart.dto.SupplierResponse;
import com.stocksmart.service.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supplier master-data endpoints (Day 2).
 * Reads: ADMIN, INVENTORY_MANAGER, PROCUREMENT_USER, MANAGEMENT.
 * Writes: ADMIN + PROCUREMENT_USER.
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers", description = "Supplier master-data management (Day 2)")
@SecurityRequirement(name = "bearerAuth")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','PROCUREMENT_USER','MANAGEMENT')")
    @Operation(summary = "List/search suppliers with pagination")
    public ResponseEntity<Page<SupplierResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(supplierService.list(search, active, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER','PROCUREMENT_USER','MANAGEMENT')")
    @Operation(summary = "Get a supplier by id")
    public ResponseEntity<SupplierResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_USER')")
    @Operation(summary = "Create a supplier (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplierService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_USER')")
    @Operation(summary = "Update a supplier (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<SupplierResponse> update(
            @PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_USER')")
    @Operation(summary = "Delete a supplier (ADMIN, PROCUREMENT_USER)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
