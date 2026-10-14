package com.stocksmart.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** One line within a sales order create/update payload. */
public class SalesOrderLineRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Ordered quantity is required")
    @Positive(message = "Ordered quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    private BigDecimal orderedQuantity;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.00", message = "Unit price must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Unit price must have at most 2 decimal places")
    private BigDecimal unitPrice;

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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
