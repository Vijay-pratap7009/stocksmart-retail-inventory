package com.stocksmart.dto;

import java.time.Instant;

/** Read model for one RFID tag mapping. */
public class RfidTagResponse {

    private Long id;
    private String epcCode;
    private Long productId;
    private String sku;
    private String productName;
    private boolean active;
    private String registeredByUsername;
    private Instant createdAt;
    private Instant updatedAt;

    public RfidTagResponse() {
    }

    public RfidTagResponse(Long id, String epcCode, Long productId, String sku,
                           String productName, boolean active, String registeredByUsername,
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.epcCode = epcCode;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.active = active;
        this.registeredByUsername = registeredByUsername;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEpcCode() {
        return epcCode;
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

    public boolean isActive() {
        return active;
    }

    public String getRegisteredByUsername() {
        return registeredByUsername;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
