package com.stocksmart.repository;

import com.stocksmart.entity.Inventory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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
}
