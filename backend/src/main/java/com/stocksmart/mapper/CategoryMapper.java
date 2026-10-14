package com.stocksmart.mapper;

import com.stocksmart.dto.CategoryResponse;
import com.stocksmart.entity.Category;
import org.springframework.stereotype.Component;

/** Converts {@link Category} entities to response DTOs (entities never leave the service layer). */
@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
