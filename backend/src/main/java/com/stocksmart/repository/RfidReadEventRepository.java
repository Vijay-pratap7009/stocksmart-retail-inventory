package com.stocksmart.repository;

import com.stocksmart.entity.RfidReadEvent;
import com.stocksmart.entity.RfidReadStatus;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for processed {@link RfidReadEvent} rows (Day 5). */
public interface RfidReadEventRepository extends JpaRepository<RfidReadEvent, Long> {

    boolean existsByEventReferenceIgnoreCase(String eventReference);

    @Query("SELECT e FROM RfidReadEvent e "
            + "WHERE (:epcCode IS NULL OR LOWER(e.epcCode) LIKE LOWER(CONCAT('%', :epcCode, '%'))) "
            + "AND (:status IS NULL OR e.status = :status) "
            + "AND (:simulated IS NULL OR e.simulated = :simulated) "
            + "AND (:from IS NULL OR e.receivedAt >= :from) "
            + "AND (:to IS NULL OR e.receivedAt <= :to)")
    Page<RfidReadEvent> search(@Param("epcCode") String epcCode,
                               @Param("status") RfidReadStatus status,
                               @Param("simulated") Boolean simulated,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);
}
