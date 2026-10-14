package com.stocksmart.dto;

import java.time.Instant;

/** Read model for {@link com.stocksmart.entity.Warehouse}. */
public class WarehouseResponse {

    private Long id;
    private String code;
    private String name;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String contactPerson;
    private String contactEmail;
    private String contactPhone;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public WarehouseResponse() {
    }

    public WarehouseResponse(Long id, String code, String name, String address, String city,
                             String state, String postalCode, String contactPerson,
                             String contactEmail, String contactPhone, boolean active,
                             Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.address = address;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.contactPerson = contactPerson;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
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
