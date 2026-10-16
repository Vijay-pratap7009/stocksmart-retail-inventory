package com.stocksmart.repository;

import com.stocksmart.entity.RfidTag;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link RfidTag} EPC-to-product mappings (Day 5). */
public interface RfidTagRepository extends JpaRepository<RfidTag, Long> {

    Optional<RfidTag> findByEpcCodeIgnoreCase(String epcCode);

    boolean existsByEpcCodeIgnoreCase(String epcCode);

    boolean existsByProductId(Long productId);

    Optional<RfidTag> findFirstByActiveTrueOrderByIdAsc();

    @Query("SELECT t FROM RfidTag t JOIN t.product p "
            + "WHERE (:active IS NULL OR t.active = :active) "
            + "AND (:productId IS NULL OR p.id = :productId) "
            + "AND (:search IS NULL OR LOWER(t.epcCode) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<RfidTag> search(@Param("search") String search,
                         @Param("active") Boolean active,
                         @Param("productId") Long productId,
                         Pageable pageable);
}
