package com.stocksmart.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create/update payload for {@link com.stocksmart.entity.Supplier}. */
public class SupplierRequest {

    @NotBlank(message = "Supplier code is required")
    @Size(min = 2, max = 30, message = "Supplier code must be 2-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Supplier code may contain only letters, digits, dash and underscore")
    private String code;

    @NotBlank(message = "Supplier name is required")
    @Size(min = 2, max = 150, message = "Supplier name must be 2-150 characters")
    private String name;

    @Size(max = 120, message = "Contact person must be at most 120 characters")
    private String contactPerson;

    @Email(message = "Email must be valid")
    @Size(max = 120, message = "Email must be at most 120 characters")
    private String email;

    @Pattern(regexp = "^$|^[+0-9()\\-\\s]{6,30}$", message = "Phone number is invalid")
    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @Size(max = 100, message = "State must be at most 100 characters")
    private String state;

    @Size(max = 20, message = "Postal code must be at most 20 characters")
    private String postalCode;

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

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
