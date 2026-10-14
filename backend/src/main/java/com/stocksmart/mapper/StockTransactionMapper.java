package com.stocksmart.mapper;

import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.entity.StockTransaction;
import org.springframework.stereotype.Component;

/** Converts {@link StockTransaction} rows to response DTOs (entities never leave the service layer). */
@Component
public class StockTransactionMapper {

    public StockTransactionResponse toResponse(StockTransaction transaction) {
        return new StockTransactionResponse(
                transaction.getId(),
                transaction.getProduct().getId(),
                transaction.getProduct().getSku(),
                transaction.getProduct().getName(),
                transaction.getWarehouse().getId(),
                transaction.getWarehouse().getCode(),
                transaction.getWarehouse().getName(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getQuantityBefore(),
                transaction.getQuantityAfter(),
                transaction.getReferenceNumber(),
                transaction.getReason(),
                transaction.getPerformedBy().getUsername(),
                transaction.getNotes(),
                transaction.getCreatedAt());
    }
}
