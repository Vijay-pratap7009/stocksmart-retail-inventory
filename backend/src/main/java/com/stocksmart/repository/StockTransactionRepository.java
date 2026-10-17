package com.stocksmart.repository;

import com.stocksmart.entity.StockTransaction;
import com.stocksmart.entity.TransactionType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for append-only {@link StockTransaction} history. */
public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {

    long countByReferenceNumber(String referenceNumber);

    boolean existsByProductId(Long productId);

    @Query("SELECT t FROM StockTransaction t "
            + "WHERE (:productId IS NULL OR t.product.id = :productId) "
            + "AND (:warehouseId IS NULL OR t.warehouse.id = :warehouseId) "
            + "AND (:type IS NULL OR t.type = :type) "
            + "AND (:reference IS NULL OR LOWER(t.referenceNumber) LIKE LOWER(CONCAT('%', :reference, '%'))) "
            + "AND (:from IS NULL OR t.createdAt >= :from) "
            + "AND (:to IS NULL OR t.createdAt <= :to)")
    Page<StockTransaction> search(@Param("productId") Long productId,
                                  @Param("warehouseId") Long warehouseId,
                                  @Param("type") TransactionType type,
                                  @Param("reference") String reference,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
