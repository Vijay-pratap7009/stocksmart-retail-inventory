package com.stocksmart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read model for a fulfillment with its lines. */
public class FulfillmentResponse {

    /** Read model for one issued line. */
    public static class Line {
        private Long id;
        private Long soItemId;
        private Long productId;
        private String sku;
        private BigDecimal quantity;

        public Line() {
        }

        public Line(Long id, Long soItemId, Long productId, String sku, BigDecimal quantity) {
            this.id = id;
            this.soItemId = soItemId;
            this.productId = productId;
            this.sku = sku;
            this.quantity = quantity;
        }

        public Long getId() {
            return id;
        }

        public Long getSoItemId() {
            return soItemId;
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
    private String fulfillmentNumber;
    private Long salesOrderId;
    private String soNumber;
    private String fulfilledByUsername;
    private Instant fulfilledAt;
    private String notes;
    private List<Line> lines;

    public FulfillmentResponse() {
    }

    public FulfillmentResponse(Long id, String fulfillmentNumber, Long salesOrderId,
                               String soNumber, String fulfilledByUsername, Instant fulfilledAt,
                               String notes, List<Line> lines) {
        this.id = id;
        this.fulfillmentNumber = fulfillmentNumber;
        this.salesOrderId = salesOrderId;
        this.soNumber = soNumber;
        this.fulfilledByUsername = fulfilledByUsername;
        this.fulfilledAt = fulfilledAt;
        this.notes = notes;
        this.lines = lines;
    }

    public Long getId() {
        return id;
    }

    public String getFulfillmentNumber() {
        return fulfillmentNumber;
    }

    public Long getSalesOrderId() {
        return salesOrderId;
    }

    public String getSoNumber() {
        return soNumber;
    }

    public String getFulfilledByUsername() {
        return fulfilledByUsername;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<Line> getLines() {
        return lines;
    }
}
