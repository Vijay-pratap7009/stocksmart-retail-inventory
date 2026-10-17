package com.stocksmart.repository;

import com.stocksmart.entity.Inventory;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link Inventory} rows.
 * Stock-changing code must use {@link #lockByProductAndWarehouse} so
 * concurrent movements serialize on the row (pessimistic write).
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.warehouse.id = :warehouseId")
    Optional<Inventory> lockByProductAndWarehouse(@Param("productId") Long productId,
                                                 @Param("warehouseId") Long warehouseId);

    boolean existsByProductId(Long productId);

    boolean existsByWarehouseId(Long warehouseId);

    List<Inventory> findByProductId(Long productId);

    List<Inventory> findByWarehouseId(Long warehouseId);

    @Query("SELECT i FROM Inventory i JOIN i.product p "
            + "WHERE (:productId IS NULL OR p.id = :productId) "
            + "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) "
            + "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Inventory> search(@Param("search") String search,
                           @Param("productId") Long productId,
                           @Param("warehouseId") Long warehouseId,
                           Pageable pageable);

    // ---- Day 5 KPI aggregations ----
    // Low stock (product-warehouse level): 0 < quantity_on_hand < product minimum
    // stock level. Legacy rows without a threshold behave as "no threshold" (0).
    // Stockout: quantity_on_hand = 0.

    @Query("SELECT COALESCE(SUM(i.quantityOnHand), 0) FROM Inventory i "
            + "WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)")
    BigDecimal sumQuantityOnHand(@Param("warehouseId") Long warehouseId);

    /**
     * Inventory value at the current product cost basis:
     * {@code SUM(quantity_on_hand * product.cost_price)}. Reporting estimate,
     * not an accounting valuation.
     */
    @Query("SELECT COALESCE(SUM(i.quantityOnHand * i.product.costPrice), 0) FROM Inventory i "
            + "WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)")
    BigDecimal sumInventoryValue(@Param("warehouseId") Long warehouseId);

    @Query("SELECT COUNT(i) FROM Inventory i "
            + "WHERE i.quantityOnHand > 0 "
            + "AND i.quantityOnHand < COALESCE(i.product.minimumStockLevel, 0) "
            + "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)")
    long countLowStock(@Param("warehouseId") Long warehouseId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.warehouse "
            + "WHERE i.quantityOnHand > 0 "
            + "AND i.quantityOnHand < COALESCE(i.product.minimumStockLevel, 0) "
            + "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)")
    Page<Inventory> findLowStock(@Param("warehouseId") Long warehouseId, Pageable pageable);

    @Query("SELECT COUNT(i) FROM Inventory i "
            + "WHERE i.quantityOnHand = 0 "
            + "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)")
    long countStockouts(@Param("warehouseId") Long warehouseId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.warehouse "
            + "WHERE i.quantityOnHand = 0 "
            + "AND (:warehouseId IS NULL OR i.warehouse.id = :warehouseId)")
    Page<Inventory> findStockouts(@Param("warehouseId") Long warehouseId, Pageable pageable);
}
