package com.stocksmart.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for one product-warehouse stock balance. */
public class InventoryResponse {

    private Long id;
    private Long productId;
    private String sku;
    private String productName;
    private String unit;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal quantityOnHand;
    private Instant createdAt;
    private Instant updatedAt;

    public InventoryResponse() {
    }

    public InventoryResponse(Long id, Long productId, String sku, String productName, String unit,
                             Long warehouseId, String warehouseCode, String warehouseName,
                             BigDecimal quantityOnHand, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.unit = unit;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.quantityOnHand = quantityOnHand;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getUnit() {
        return unit;
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

    public BigDecimal getQuantityOnHand() {
        return quantityOnHand;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
