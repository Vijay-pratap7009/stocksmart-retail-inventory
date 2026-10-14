package com.stocksmart.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** One line within a purchase order create/update payload. */
public class PurchaseOrderLineRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Ordered quantity is required")
    @Positive(message = "Ordered quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    private BigDecimal orderedQuantity;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(value = "0.00", message = "Unit cost must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Unit cost must have at most 2 decimal places")
    private BigDecimal unitCost;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getOrderedQuantity() {
        return orderedQuantity;
    }

    public void setOrderedQuantity(BigDecimal orderedQuantity) {
        this.orderedQuantity = orderedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }
}
