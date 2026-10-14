package com.stocksmart.repository;

import com.stocksmart.entity.SalesFulfillment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link SalesFulfillment} audit records. */
public interface SalesFulfillmentRepository extends JpaRepository<SalesFulfillment, Long> {

    boolean existsByFulfillmentNumberIgnoreCase(String fulfillmentNumber);

    List<SalesFulfillment> findBySalesOrderIdOrderByFulfilledAtDesc(Long salesOrderId);
}
