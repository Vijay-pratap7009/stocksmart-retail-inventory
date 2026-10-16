package com.stocksmart.mapper;

import com.stocksmart.dto.ProductResponse;
import com.stocksmart.entity.Product;
import org.springframework.stereotype.Component;

/** Converts {@link Product} entities to response DTOs (entities never leave the service layer). */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getBarcode(),
                product.getName(),
                product.getDescription(),
                product.getCategory() != null ? product.getCategory().getId() : null,
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getCategory() != null ? product.getCategory().getCode() : null,
                product.getBrand(),
                product.getUnit(),
                product.getCostPrice(),
                product.getSellingPrice(),
                product.getEffectiveMinimumStockLevel(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
