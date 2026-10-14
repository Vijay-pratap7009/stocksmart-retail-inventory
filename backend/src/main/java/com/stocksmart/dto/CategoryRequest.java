package com.stocksmart.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

/** Create/update payload for {@link com.stocksmart.entity.Category}. */
public class CategoryRequest {

    @NotBlank(message = "Category code is required")
    @Size(min = 2, max = 30, message = "Category code must be 2-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "Category code may contain only letters, digits, dash and underscore")
    private String code;

    @NotBlank(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be 2-100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
