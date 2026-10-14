package com.stocksmart.repository;

import com.stocksmart.entity.Supplier;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Supplier} master data. */
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT s FROM Supplier s "
            + "WHERE (:active IS NULL OR s.active = :active) "
            + "AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(s.code) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(s.contactPerson) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Supplier> search(@Param("search") String search,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
