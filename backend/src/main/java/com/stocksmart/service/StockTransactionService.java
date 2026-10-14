package com.stocksmart.service;

import com.stocksmart.dto.StockTransactionResponse;
import com.stocksmart.entity.TransactionType;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.StockTransactionMapper;
import com.stocksmart.repository.StockTransactionRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only stock movement history (Day 3). History rows are append-only — no writes here. */
@Service
public class StockTransactionService {

    private final StockTransactionRepository transactionRepository;
    private final StockTransactionMapper transactionMapper;

    public StockTransactionService(StockTransactionRepository transactionRepository,
                                   StockTransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional(readOnly = true)
    public Page<StockTransactionResponse> search(Long productId, Long warehouseId,
                                                 TransactionType type, String reference,
                                                 Instant from, Instant to, Pageable pageable) {
        return transactionRepository
                .search(productId, warehouseId, type, normalize(reference), from, to, pageable)
                .map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public StockTransactionResponse get(Long id) {
        return transactionRepository.findById(id)
                .map(transactionMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock transaction " + id + " not found"));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
