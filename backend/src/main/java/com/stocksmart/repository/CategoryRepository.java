package com.stocksmart.repository;

import com.stocksmart.entity.Category;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Category} master data. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT c FROM Category c "
            + "WHERE (:active IS NULL OR c.active = :active) "
            + "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(c.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Category> search(@Param("search") String search,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
