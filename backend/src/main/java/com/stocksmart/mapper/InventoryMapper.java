package com.stocksmart.mapper;

import com.stocksmart.dto.InventoryResponse;
import com.stocksmart.entity.Inventory;
import org.springframework.stereotype.Component;

/** Converts {@link Inventory} rows to response DTOs (entities never leave the service layer). */
@Component
public class InventoryMapper {

    public InventoryResponse toResponse(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getProduct().getUnit(),
                inventory.getWarehouse().getId(),
                inventory.getWarehouse().getCode(),
                inventory.getWarehouse().getName(),
                inventory.getQuantityOnHand(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt());
    }
}
