package com.stocksmart.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** One fulfilled line: quantity of an SO line issued in this fulfillment. */
public class FulfillmentLineRequest {

    @NotNull(message = "SO line is required")
    private Long soItemId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    private BigDecimal quantity;

    public Long getSoItemId() {
        return soItemId;
    }

    public void setSoItemId(Long soItemId) {
        this.soItemId = soItemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
