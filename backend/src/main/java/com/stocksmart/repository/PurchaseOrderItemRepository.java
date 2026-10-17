package com.stocksmart.repository;

import com.stocksmart.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link PurchaseOrderItem} lines (existence checks for delete guards). */
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    boolean existsByProductId(Long productId);
}
