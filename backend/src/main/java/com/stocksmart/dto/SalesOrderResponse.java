package com.stocksmart.dto;

import com.stocksmart.entity.SalesOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Read model for a sales order header with lines and totals. */
public class SalesOrderResponse {

    private Long id;
    private String soNumber;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String customerName;
    private String customerContact;
    private LocalDate orderDate;
    private SalesOrderStatus status;
    private String notes;
    private String createdByUsername;
    private List<SalesOrderLineResponse> items;
    private BigDecimal orderTotal;
    private Instant createdAt;
    private Instant updatedAt;

    public SalesOrderResponse() {
    }

    public SalesOrderResponse(Long id, String soNumber, Long warehouseId, String warehouseCode,
                              String warehouseName, String customerName, String customerContact,
                              LocalDate orderDate, SalesOrderStatus status, String notes,
                              String createdByUsername, List<SalesOrderLineResponse> items,
                              Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.soNumber = soNumber;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.customerName = customerName;
        this.customerContact = customerContact;
        this.orderDate = orderDate;
        this.status = status;
        this.notes = notes;
        this.createdByUsername = createdByUsername;
        this.items = items;
        this.orderTotal = items.stream()
                .map(SalesOrderLineResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSoNumber() {
        return soNumber;
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

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerContact() {
        return customerContact;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public List<SalesOrderLineResponse> getItems() {
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
