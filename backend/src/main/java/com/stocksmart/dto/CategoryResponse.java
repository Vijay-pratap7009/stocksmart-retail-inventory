package com.stocksmart.dto;

import java.time.Instant;

/** Read model for {@link com.stocksmart.entity.Category} — never exposes internals beyond safe fields. */
public class CategoryResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public CategoryResponse() {
    }

    public CategoryResponse(Long id, String code, String name, String description,
                            boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
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

    public String getDescription() {
        return description;
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
