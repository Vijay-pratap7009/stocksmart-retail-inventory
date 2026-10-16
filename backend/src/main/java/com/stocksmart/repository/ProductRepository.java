package com.stocksmart.repository;

import com.stocksmart.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Product} master data. */
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    Optional<Product> findByBarcodeIgnoreCase(String barcode);

    boolean existsByBarcodeIgnoreCase(String barcode);

    boolean existsByCategoryId(Long categoryId);

    long countByActiveTrue();

    @Query("SELECT p FROM Product p "
            + "WHERE (:active IS NULL OR p.active = :active) "
            + "AND (:categoryId IS NULL OR p.category.id = :categoryId) "
            + "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> search(@Param("search") String search,
                         @Param("active") Boolean active,
                         @Param("categoryId") Long categoryId,
                         Pageable pageable);
}
