package com.stocksmart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * WAREHOUSES — storage-location master data (Day 2).
 *
 * <p>Standalone master table. Inventory balances and stock transfers
 * belong to Day 3 and are deliberately absent here.
 */
@Entity
@Table(
        name = "warehouses",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_warehouses_code", columnNames = "warehouse_code")
        }
)
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "warehouses_seq")
    @SequenceGenerator(name = "warehouses_seq", sequenceName = "warehouses_seq", allocationSize = 1)
    @Column(name = "warehouse_id")
    private Long id;

    /** Business identifier — unique. */
    @NotBlank(message = "Warehouse code is required")
    @Size(min = 2, max = 30, message = "Warehouse code must be 2-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Warehouse code may contain only letters, digits, dash and underscore")
    @Column(name = "warehouse_code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank(message = "Warehouse name is required")
    @Size(min = 2, max = 150, message = "Warehouse name must be 2-150 characters")
    @Column(name = "warehouse_name", nullable = false, length = 150)
    private String name;

    @Size(max = 255, message = "Address must be at most 255 characters")
    @Column(name = "address", length = 255)
    private String address;

    @Size(max = 100, message = "City must be at most 100 characters")
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100, message = "State must be at most 100 characters")
    @Column(name = "state", length = 100)
    private String state;

    @Size(max = 20, message = "Postal code must be at most 20 characters")
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Size(max = 120, message = "Contact person must be at most 120 characters")
    @Column(name = "contact_person", length = 120)
    private String contactPerson;

    @Email(message = "Contact email must be valid")
    @Size(max = 120, message = "Contact email must be at most 120 characters")
    @Column(name = "contact_email", length = 120)
    private String contactEmail;

    @Pattern(regexp = "^$|^[+0-9()\\-\\s]{6,30}$", message = "Contact phone is invalid")
    @Size(max = 30, message = "Contact phone must be at most 30 characters")
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @NotNull
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Warehouse() {
    }

    public Warehouse(String code, String name, String address, String city, String state,
                     String postalCode, String contactPerson, String contactEmail,
                     String contactPhone, boolean active) {
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
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
