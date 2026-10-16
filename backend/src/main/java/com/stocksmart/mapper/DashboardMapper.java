package com.stocksmart.mapper;

import com.stocksmart.dto.LowStockItemResponse;
import com.stocksmart.dto.StockoutItemResponse;
import com.stocksmart.entity.Inventory;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Converts inventory rows to Day 5 dashboard KPI DTOs. */
@Component
public class DashboardMapper {

    public LowStockItemResponse toLowStockItem(Inventory inventory) {
        BigDecimal minimum = inventory.getProduct().getEffectiveMinimumStockLevel();
        BigDecimal deficit = minimum.subtract(inventory.getQuantityOnHand());
        return new LowStockItemResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getProduct().getUnit(),
                inventory.getWarehouse().getId(),
                inventory.getWarehouse().getCode(),
                inventory.getWarehouse().getName(),
                inventory.getQuantityOnHand(),
                minimum,
                deficit);
    }

    public StockoutItemResponse toStockoutItem(Inventory inventory) {
        return new StockoutItemResponse(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getProduct().getUnit(),
                inventory.getWarehouse().getId(),
                inventory.getWarehouse().getCode(),
                inventory.getWarehouse().getName(),
                inventory.getQuantityOnHand(),
                inventory.getUpdatedAt());
    }
}
