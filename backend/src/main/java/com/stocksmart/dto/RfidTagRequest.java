package com.stocksmart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create payload for an RFID tag mapping (Day 5). */
public class RfidTagRequest {

    @NotBlank(message = "EPC code is required")
    @Size(min = 4, max = 96, message = "EPC code must be 4-96 characters")
    @Pattern(regexp = "^[A-Za-z0-9.\\-:]{4,96}$",
            message = "EPC code may contain only letters, digits, dot, dash and colon")
    private String epcCode;

    @NotNull(message = "Product is required")
    private Long productId;

    private Boolean active;

    public String getEpcCode() {
        return epcCode;
    }

    public void setEpcCode(String epcCode) {
        this.epcCode = epcCode;
    }

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
