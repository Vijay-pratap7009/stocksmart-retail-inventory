package com.stocksmart.dto;

import com.stocksmart.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;

/** Read model for one stock movement audit row. */
public class StockTransactionResponse {

    private Long id;
    private Long productId;
    private String sku;
    private String productName;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private TransactionType type;
    private BigDecimal quantity;
    private BigDecimal quantityBefore;
    private BigDecimal quantityAfter;
    private String referenceNumber;
    private String reason;
    private String performedByUsername;
    private String notes;
    private Instant createdAt;

    public StockTransactionResponse() {
    }

    public StockTransactionResponse(Long id, Long productId, String sku, String productName,
                                    Long warehouseId, String warehouseCode, String warehouseName,
                                    TransactionType type, BigDecimal quantity,
                                    BigDecimal quantityBefore, BigDecimal quantityAfter,
                                    String referenceNumber, String reason,
                                    String performedByUsername, String notes, Instant createdAt) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.type = type;
        this.quantity = quantity;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.referenceNumber = referenceNumber;
        this.reason = reason;
        this.performedByUsername = performedByUsername;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
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

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getReason() {
        return reason;
    }

    public String getPerformedByUsername() {
        return performedByUsername;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
