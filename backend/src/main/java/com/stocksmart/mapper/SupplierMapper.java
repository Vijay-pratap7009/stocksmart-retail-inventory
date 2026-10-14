package com.stocksmart.mapper;

import com.stocksmart.dto.SupplierResponse;
import com.stocksmart.entity.Supplier;
import org.springframework.stereotype.Component;

/** Converts {@link Supplier} entities to response DTOs (entities never leave the service layer). */
@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactPerson(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getCity(),
                supplier.getState(),
                supplier.getPostalCode(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
    }
}
