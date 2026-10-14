package com.stocksmart.repository;

import com.stocksmart.entity.PurchaseReceipt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link PurchaseReceipt} audit records. */
public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, Long> {

    boolean existsByReceiptNumberIgnoreCase(String receiptNumber);

    List<PurchaseReceipt> findByPurchaseOrderIdOrderByReceivedAtDesc(Long purchaseOrderId);
}
