package com.stocksmart.controller;

import com.stocksmart.dto.RfidReadEventResponse;
import com.stocksmart.dto.RfidReadRequest;
import com.stocksmart.dto.RfidReaderInfoResponse;
import com.stocksmart.dto.RfidTagRequest;
import com.stocksmart.dto.RfidTagResponse;
import com.stocksmart.dto.RfidTagUpdateRequest;
import com.stocksmart.entity.RfidReadStatus;
import com.stocksmart.service.RfidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
 * RFID endpoints (Day 5).
 *
 * <p>Reads (tag list/lookup, read history, reader info): any authenticated
 * role. Tag mapping writes (register/update/deactivate): ADMIN only. Read
 * processing: operational roles + sales (ADMIN, INVENTORY_MANAGER,
 * WAREHOUSE_STAFF, PROCUREMENT_USER, SALES_USER) — MANAGEMENT is read-only.
 *
 * <p>Reads are informational and never change stock; stock-changing actions
 * go through the inventory/order workflows with their own authorization.
 */
@RestController
@RequestMapping("/api/v1/rfid")
@Tag(name = "RFID", description = "RFID tag mapping and reader/simulator integration (Day 5)")
@SecurityRequirement(name = "bearerAuth")
public class RfidController {

    private static final String READ_PROCESSOR =
            "hasAnyRole('ADMIN','INVENTORY_MANAGER','WAREHOUSE_STAFF','PROCUREMENT_USER','SALES_USER')";

    private final RfidService rfidService;

    public RfidController(RfidService rfidService) {
        this.rfidService = rfidService;
    }

    @GetMapping("/tags")
    @Operation(summary = "List/filter RFID tag mappings (any authenticated role)")
    public ResponseEntity<Page<RfidTagResponse>> listTags(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(rfidService.listTags(search, active, productId, pageable));
    }

    @GetMapping("/tags/{epcCode}")
    @Operation(summary = "Look up an RFID tag mapping by EPC code")
    public ResponseEntity<RfidTagResponse> getTag(@PathVariable String epcCode) {
        return ResponseEntity.ok(rfidService.getTag(epcCode));
    }

    @PostMapping("/tags")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register an RFID tag mapping (ADMIN)")
    public ResponseEntity<RfidTagResponse> registerTag(@Valid @RequestBody RfidTagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rfidService.registerTag(request));
    }

    @PutMapping("/tags/{epcCode}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remap or (de)activate an RFID tag (ADMIN)")
    public ResponseEntity<RfidTagResponse> updateTag(
            @PathVariable String epcCode, @Valid @RequestBody RfidTagUpdateRequest request) {
        return ResponseEntity.ok(rfidService.updateTag(epcCode, request));
    }

    @GetMapping("/reader")
    @Operation(summary = "Describe the active RFID reader adapter (simulator or hardware bridge)")
    public ResponseEntity<RfidReaderInfoResponse> readerInfo() {
        return ResponseEntity.ok(rfidService.readerInfo());
    }

    @PostMapping("/reads")
    @PreAuthorize(READ_PROCESSOR)
    @Operation(summary = "Process one RFID read through the reader abstraction — never changes stock")
    public ResponseEntity<RfidReadEventResponse> processRead(
            @Valid @RequestBody RfidReadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rfidService.processRead(request));
    }

    @GetMapping("/reads")
    @Operation(summary = "Search processed RFID read events (newest first)")
    public ResponseEntity<Page<RfidReadEventResponse>> listReads(
            @RequestParam(required = false) String epcCode,
            @RequestParam(required = false) RfidReadStatus status,
            @RequestParam(required = false) Boolean simulated,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(
                rfidService.listReads(epcCode, status, simulated, from, to, pageable));
    }
}
