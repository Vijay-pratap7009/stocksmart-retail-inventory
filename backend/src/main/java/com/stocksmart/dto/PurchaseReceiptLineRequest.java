package com.stocksmart.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** One received line: quantity of a PO line arriving in this receipt. */
public class PurchaseReceiptLineRequest {

    @NotNull(message = "PO line is required")
    private Long poItemId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    private BigDecimal quantity;

    public Long getPoItemId() {
        return poItemId;
    }

    public void setPoItemId(Long poItemId) {
        this.poItemId = poItemId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
