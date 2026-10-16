package com.stocksmart.repository;

import com.stocksmart.entity.Warehouse;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Warehouse} master data. */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    long countByActiveTrue();

    @Query("SELECT w FROM Warehouse w "
            + "WHERE (:active IS NULL OR w.active = :active) "
            + "AND (:search IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(w.code) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(w.city) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Warehouse> search(@Param("search") String search,
                           @Param("active") Boolean active,
                           Pageable pageable);
}
