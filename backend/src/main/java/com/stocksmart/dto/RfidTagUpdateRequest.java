package com.stocksmart.dto;

import jakarta.validation.constraints.NotNull;

/** Update payload for an RFID tag mapping (product remap / activate / deactivate). */
public class RfidTagUpdateRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Active flag is required")
    private Boolean active;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
