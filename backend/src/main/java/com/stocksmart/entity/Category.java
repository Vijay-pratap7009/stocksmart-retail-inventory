package com.stocksmart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * CATEGORIES — product grouping master data (Day 2).
 *
 * <p>Parent side of CATEGORIES 1—N PRODUCTS. A category referenced by
 * products must not be hard-deleted (service returns 409 instead).
 *
 * <p>Oracle mapping: NUMBER(19) PK via sequence, VARCHAR2 business columns.
 */
@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_categories_code", columnNames = "category_code")
        }
)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "categories_seq")
    @SequenceGenerator(name = "categories_seq", sequenceName = "categories_seq", allocationSize = 1)
    @Column(name = "category_id")
    private Long id;

    /** Business identifier — unique, uppercase alphanumeric + dash/underscore. */
    @NotBlank(message = "Category code is required")
    @Size(min = 2, max = 30, message = "Category code must be 2-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Category code may contain only letters, digits, dash and underscore")
    @Column(name = "category_code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be 2-100 characters")
    @Column(name = "category_name", nullable = false, length = 100)
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Category() {
    }

    public Category(String code, String name, String description, boolean active) {
        this.code = code;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
