package com.stocksmart.repository;

import com.stocksmart.entity.SalesOrder;
import com.stocksmart.entity.SalesOrderStatus;
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
 * Persistence for {@link SalesOrder} headers.
 * Fulfillment operations must use {@link #lockById} so concurrent
 * fulfillments on the same order serialize instead of double-issuing.
 */
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findBySoNumberIgnoreCase(String soNumber);

    boolean existsBySoNumberIgnoreCase(String soNumber);

    boolean existsByWarehouseId(Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SalesOrder o WHERE o.id = :id")
    Optional<SalesOrder> lockById(@Param("id") Long id);

    @Query("SELECT o FROM SalesOrder o "
            + "WHERE (:status IS NULL OR o.status = :status) "
            + "AND (:warehouseId IS NULL OR o.warehouse.id = :warehouseId) "
            + "AND (:search IS NULL OR LOWER(o.soNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(o.customerName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<SalesOrder> search(@Param("search") String search,
                            @Param("status") SalesOrderStatus status,
                            @Param("warehouseId") Long warehouseId,
                            Pageable pageable);

    /**
     * Day 5 KPI aggregation: order counts per status, optionally scoped to a
     * warehouse. Rows are counted once (no joins to lines).
     */
    @Query("SELECT o.status, COUNT(o) FROM SalesOrder o "
            + "WHERE (:warehouseId IS NULL OR o.warehouse.id = :warehouseId) "
            + "GROUP BY o.status")
    List<Object[]> countByStatus(@Param("warehouseId") Long warehouseId);
}
