package com.stocksmart.controller;

import com.stocksmart.dto.ProductRequest;
import com.stocksmart.dto.ProductResponse;
import com.stocksmart.service.ProductService;
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
 * Product master-data endpoints (Day 2).
 * Reads: any authenticated role. Writes: ADMIN + INVENTORY_MANAGER.
 * Stock quantities are Day 3 scope and intentionally absent.
 */
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product master-data management (Day 2)")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List/search products with pagination, category and active filters")
    public ResponseEntity<Page<ProductResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(productService.list(search, active, categoryId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ResponseEntity<ProductResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(productService.get(id));
    }

    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Identify a product by scanned/manually entered barcode (read-only, never changes stock)")
    public ResponseEntity<ProductResponse> getByBarcode(@PathVariable String barcode) {
        return ResponseEntity.ok(productService.getByBarcode(barcode));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Identify a product by SKU (read-only, never changes stock)")
    public ResponseEntity<ProductResponse> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getBySku(sku));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Create a product (ADMIN, INVENTORY_MANAGER)")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Update a product (ADMIN, INVENTORY_MANAGER)")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','INVENTORY_MANAGER')")
    @Operation(summary = "Delete a product (ADMIN, INVENTORY_MANAGER)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
