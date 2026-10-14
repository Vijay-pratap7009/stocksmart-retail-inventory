package com.stocksmart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read model for a goods receipt with its lines. */
public class PurchaseReceiptResponse {

    /** Read model for one received line. */
    public static class Line {
        private Long id;
        private Long poItemId;
        private Long productId;
        private String sku;
        private BigDecimal quantity;

        public Line() {
        }

        public Line(Long id, Long poItemId, Long productId, String sku, BigDecimal quantity) {
            this.id = id;
            this.poItemId = poItemId;
            this.productId = productId;
            this.sku = sku;
            this.quantity = quantity;
        }

        public Long getId() {
            return id;
        }

        public Long getPoItemId() {
            return poItemId;
        }

        public Long getProductId() {
            return productId;
        }

        public String getSku() {
            return sku;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }
    }

    private Long id;
    private String receiptNumber;
    private Long purchaseOrderId;
    private String poNumber;
    private String receivedByUsername;
    private Instant receivedAt;
    private String notes;
    private List<Line> lines;

    public PurchaseReceiptResponse() {
    }

    public PurchaseReceiptResponse(Long id, String receiptNumber, Long purchaseOrderId, String poNumber,
                           String receivedByUsername, Instant receivedAt, String notes,
                           List<Line> lines) {
        this.id = id;
        this.receiptNumber = receiptNumber;
        this.purchaseOrderId = purchaseOrderId;
        this.poNumber = poNumber;
        this.receivedByUsername = receivedByUsername;
        this.receivedAt = receivedAt;
        this.notes = notes;
        this.lines = lines;
    }

    public Long getId() {
        return id;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public String getReceivedByUsername() {
        return receivedByUsername;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<Line> getLines() {
        return lines;
    }
}
