package com.stocksmart.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/update payload for {@link com.stocksmart.entity.Warehouse}. */
public class WarehouseRequest {

    @NotBlank(message = "Warehouse code is required")
    @Size(min = 2, max = 30, message = "Warehouse code must be 2-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Warehouse code may contain only letters, digits, dash and underscore")
    private String code;

    @NotBlank(message = "Warehouse name is required")
    @Size(min = 2, max = 150, message = "Warehouse name must be 2-150 characters")
    private String name;

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @Size(max = 100, message = "State must be at most 100 characters")
    private String state;

    @Size(max = 20, message = "Postal code must be at most 20 characters")
    private String postalCode;

    @Size(max = 120, message = "Contact person must be at most 120 characters")
    private String contactPerson;

    @Email(message = "Contact email must be valid")
    @Size(max = 120, message = "Contact email must be at most 120 characters")
    private String contactEmail;

    @Pattern(regexp = "^$|^[+0-9()\\-\\s]{6,30}$", message = "Contact phone is invalid")
    @Size(max = 30, message = "Contact phone must be at most 30 characters")
    private String contactPhone;

    private Boolean active;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
