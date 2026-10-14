package com.stocksmart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Purchase order create/update payload. {@code poNumber} is optional —
 * generated when absent. Lines may be empty on a draft; submission
 * requires at least one line.
 */
public class PurchaseOrderRequest {

    @Size(max = 40, message = "PO number must be at most 40 characters")
    private String poNumber;

    @NotNull(message = "Supplier is required")
    private Long supplierId;

    @NotNull(message = "Destination warehouse is required")
    private Long warehouseId;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    private LocalDate expectedDeliveryDate;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    @Valid
    private List<PurchaseOrderLineRequest> items = new ArrayList<>();

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) {
        this.expectedDeliveryDate = expectedDeliveryDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<PurchaseOrderLineRequest> getItems() {
        return items;
    }

    public void setItems(List<PurchaseOrderLineRequest> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
