package com.stocksmart.controller;

import com.stocksmart.entity.TransactionType;
import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.service.StockTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock movement history endpoints (Day 3). Append-only audit trail —
 * readable by any authenticated role; no write endpoints exist by design.
 */
@RestController
@RequestMapping("/api/v1/stock-transactions")
@Tag(name = "Stock transactions", description = "Append-only movement history (Day 3)")
@SecurityRequirement(name = "bearerAuth")
public class StockTransactionController {

    private final StockTransactionService transactionService;

    public StockTransactionController(StockTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Search movement history with filters, pagination and sorting")
    public ResponseEntity<Page<StockTransactionResponse>> search(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
                transactionService.search(productId, warehouseId, type, reference, from, to, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve one transaction record")
    public ResponseEntity<StockTransactionResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.get(id));
    }
}
