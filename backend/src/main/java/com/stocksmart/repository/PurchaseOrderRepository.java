package com.stocksmart.repository;

import com.stocksmart.entity.PurchaseOrder;
import com.stocksmart.entity.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link PurchaseOrder} headers.
 * Receipt operations must use {@link #lockById} so concurrent receipts on
 * the same order serialize instead of double-receiving.
 */
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumberIgnoreCase(String poNumber);

    boolean existsByPoNumberIgnoreCase(String poNumber);

    boolean existsBySupplierId(Long supplierId);

    boolean existsByWarehouseId(Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PurchaseOrder o WHERE o.id = :id")
    Optional<PurchaseOrder> lockById(@Param("id") Long id);

    @Query("SELECT o FROM PurchaseOrder o "
            + "WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:supplierId IS NULL OR o.supplier.id = :supplierId) "
            + "AND (:search IS NULL OR LOWER(o.poNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PurchaseOrder> search(@Param("search") String search,
                               @Param("status") PurchaseOrderStatus status,
                               @Param("supplierId") Long supplierId,
                               Pageable pageable);

    /**
     * Day 5 KPI aggregation: order counts per status, optionally scoped to a
     * destination warehouse. Rows are counted once (no joins to lines).
     */
    @Query("SELECT o.status, COUNT(o) FROM PurchaseOrder o "
            + "WHERE (:warehouseId IS NULL OR o.warehouse.id = :warehouseId) "
            + "GROUP BY o.status")
    List<Object[]> countByStatus(@Param("warehouseId") Long warehouseId);
}
