package com.stocksmart.mapper;

import com.stocksmart.dto.WarehouseResponse;
import com.stocksmart.entity.Warehouse;
import org.springframework.stereotype.Component;

/** Converts {@link Warehouse} entities to response DTOs (entities never leave the service layer). */
@Component
public class WarehouseMapper {

    public WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getCity(),
                warehouse.getState(),
                warehouse.getPostalCode(),
                warehouse.getContactPerson(),
                warehouse.getContactEmail(),
                warehouse.getContactPhone(),
                warehouse.isActive(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt());
    }
}
