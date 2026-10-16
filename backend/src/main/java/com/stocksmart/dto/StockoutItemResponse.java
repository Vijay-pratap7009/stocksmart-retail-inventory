package com.stocksmart.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One stockout classification (product-warehouse level):
 * {@code quantityOnHand = 0}. A balance row exists but holds no stock —
 * the combination is recorded, never dropped from reporting.
 */
public class StockoutItemResponse {

    private Long inventoryId;
    private Long productId;
    private String sku;
    private String productName;
    private String unit;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal quantityOnHand;
    private Instant updatedAt;

    public StockoutItemResponse() {
    }

    public StockoutItemResponse(Long inventoryId, Long productId, String sku, String productName,
                                String unit, Long warehouseId, String warehouseCode,
                                String warehouseName, BigDecimal quantityOnHand,
                                Instant updatedAt) {
        this.inventoryId = inventoryId;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.unit = unit;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.quantityOnHand = quantityOnHand;
        this.updatedAt = updatedAt;
    }

    public Long getInventoryId() {
        return inventoryId;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
