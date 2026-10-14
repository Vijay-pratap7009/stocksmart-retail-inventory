package com.stocksmart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Sales order create/update payload. Lines may be empty on a draft;
 * confirmation requires at least one line.
 */
public class SalesOrderRequest {

    @Size(max = 40, message = "SO number must be at most 40 characters")
    private String soNumber;

    @NotNull(message = "Warehouse is required")
    private Long warehouseId;

    @NotBlank(message = "Customer name is required")
    @Size(min = 2, max = 150, message = "Customer name must be 2-150 characters")
    private String customerName;

    @Size(max = 120, message = "Customer contact must be at most 120 characters")
    private String customerContact;

    @NotNull(message = "Order date is required")
    private LocalDate orderDate;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    @Valid
    private List<SalesOrderLineRequest> items = new ArrayList<>();

    public String getSoNumber() {
        return soNumber;
    }

    public void setSoNumber(String soNumber) {
        this.soNumber = soNumber;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerContact() {
        return customerContact;
    }

    public void setCustomerContact(String customerContact) {
        this.customerContact = customerContact;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<SalesOrderLineRequest> getItems() {
        return items;
    }

    public void setItems(List<SalesOrderLineRequest> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
