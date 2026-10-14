package com.stocksmart.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Create/update payload for {@link com.stocksmart.entity.Product}. */
public class ProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(min = 2, max = 50, message = "SKU must be 2-50 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "SKU may contain only letters, digits, dash and underscore")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 150, message = "Product name must be 2-150 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @Size(max = 100, message = "Brand must be at most 100 characters")
    private String brand;

    @NotBlank(message = "Unit of measurement is required")
    @Size(max = 20, message = "Unit must be at most 20 characters")
    private String unit;

    @NotNull(message = "Cost price is required")
    @DecimalMin(value = "0.00", message = "Cost price must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Cost price must have at most 2 decimal places")
    private BigDecimal costPrice;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.00", message = "Selling price must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Selling price must have at most 2 decimal places")
    private BigDecimal sellingPrice;

    private Boolean active;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
