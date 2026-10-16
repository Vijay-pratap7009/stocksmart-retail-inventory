package com.stocksmart.repository;

import com.stocksmart.entity.SalesOrderItem;
import com.stocksmart.entity.SalesOrderStatus;
import java.math.BigDecimal;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link SalesOrderItem} lines (read-side aggregations for Day 5 KPIs). */
public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, Long> {

    /**
     * Day 5 KPI aggregation: total remaining quantity to fulfill across items
     * of orders in the given statuses ({@code ordered - fulfilled} per line).
     * Each line contributes exactly once — no double counting.
     */
    @Query("SELECT COALESCE(SUM(i.orderedQuantity - i.fulfilledQuantity), 0) "
            + "FROM SalesOrderItem i "
            + "WHERE i.salesOrder.status IN :statuses "
            + "AND (:warehouseId IS NULL OR i.salesOrder.warehouse.id = :warehouseId)")
    BigDecimal sumRemainingQuantity(@Param("statuses") Collection<SalesOrderStatus> statuses,
                                    @Param("warehouseId") Long warehouseId);
}
