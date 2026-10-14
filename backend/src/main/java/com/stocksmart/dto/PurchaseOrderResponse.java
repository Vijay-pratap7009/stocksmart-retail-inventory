package com.stocksmart.dto;

import com.stocksmart.entity.PurchaseOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read model for a purchase order header with lines and totals. */
public class PurchaseOrderResponse {

    private Long id;
    private String poNumber;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private LocalDate orderDate;
    private LocalDate expectedDeliveryDate;
    private PurchaseOrderStatus status;
    private String notes;
    private String createdByUsername;
    private List<PurchaseOrderLineResponse> items;
    private BigDecimal orderTotal;
    private Instant createdAt;
    private Instant updatedAt;

    public PurchaseOrderResponse() {
    }

    public PurchaseOrderResponse(Long id, String poNumber, Long supplierId, String supplierName,
                                 String supplierCode, Long warehouseId, String warehouseCode,
                                 String warehouseName, LocalDate orderDate,
                                 LocalDate expectedDeliveryDate, PurchaseOrderStatus status,
                                 String notes, String createdByUsername,
                                 List<PurchaseOrderLineResponse> items, Instant createdAt,
                                 Instant updatedAt) {
        this.id = id;
        this.poNumber = poNumber;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.supplierCode = supplierCode;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.orderDate = orderDate;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.status = status;
        this.notes = notes;
        this.createdByUsername = createdByUsername;
        this.items = items;
        this.orderTotal = items.stream()
                .map(PurchaseOrderLineResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public List<PurchaseOrderLineResponse> getItems() {
        return items;
    }

    public BigDecimal getOrderTotal() {
        return orderTotal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
