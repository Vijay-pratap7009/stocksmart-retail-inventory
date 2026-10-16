package com.stocksmart.dto;

import java.math.BigDecimal;

/**
 * One low-stock classification (product-warehouse level):
 * {@code 0 < quantityOnHand < minimumStockLevel}. {@code deficit} is the
 * quantity required to reach the minimum. A balance at exactly the minimum
 * is NOT low stock; zero quantity is a stockout, not low stock.
 */
public class LowStockItemResponse {

    private Long inventoryId;
    private Long productId;
    private String sku;
    private String productName;
    private String unit;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal quantityOnHand;
    private BigDecimal minimumStockLevel;
    private BigDecimal deficit;

    public LowStockItemResponse() {
    }

    public LowStockItemResponse(Long inventoryId, Long productId, String sku, String productName,
                                String unit, Long warehouseId, String warehouseCode,
                                String warehouseName, BigDecimal quantityOnHand,
                                BigDecimal minimumStockLevel, BigDecimal deficit) {
        this.inventoryId = inventoryId;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.unit = unit;
        this.warehouseId = warehouseId;
        this.warehouseCode = warehouseCode;
        this.warehouseName = warehouseName;
        this.quantityOnHand = quantityOnHand;
        this.minimumStockLevel = minimumStockLevel;
        this.deficit = deficit;
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

    public BigDecimal getMinimumStockLevel() {
        return minimumStockLevel;
    }

    public BigDecimal getDeficit() {
        return deficit;
    }
}
