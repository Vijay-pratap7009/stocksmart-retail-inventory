package com.stocksmart.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for {@link com.stocksmart.entity.Product}, including denormalized category info for lists. */
public class ProductResponse {

    private Long id;
    private String sku;
    private String barcode;
    private String name;
    private String description;
    private Long categoryId;
    private String categoryName;
    private String categoryCode;
    private String brand;
    private String unit;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;
    private BigDecimal minimumStockLevel;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public ProductResponse() {
    }

    public ProductResponse(Long id, String sku, String barcode, String name, String description,
                           Long categoryId, String categoryName, String categoryCode,
                           String brand, String unit, BigDecimal costPrice,
                           BigDecimal sellingPrice, BigDecimal minimumStockLevel, boolean active,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.sku = sku;
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryCode = categoryCode;
        this.brand = brand;
        this.unit = unit;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.minimumStockLevel = minimumStockLevel;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getBrand() {
        return brand;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public BigDecimal getMinimumStockLevel() {
        return minimumStockLevel;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
