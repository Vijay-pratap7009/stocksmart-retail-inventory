package com.stocksmart.dto;

import java.math.BigDecimal;

/** Read model for one sales order line, including fulfillment progress. */
public class SalesOrderLineResponse {

    private Long id;
    private Long productId;
    private String sku;
    private String productName;
    private BigDecimal orderedQuantity;
    private BigDecimal fulfilledQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public SalesOrderLineResponse() {
    }

    public SalesOrderLineResponse(Long id, Long productId, String sku, String productName,
                                  BigDecimal orderedQuantity, BigDecimal fulfilledQuantity,
                                  BigDecimal unitPrice) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.orderedQuantity = orderedQuantity;
        this.fulfilledQuantity = fulfilledQuantity;
        this.remainingQuantity = orderedQuantity.subtract(fulfilledQuantity);
        this.unitPrice = unitPrice;
        this.lineTotal = orderedQuantity.multiply(unitPrice);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getOrderedQuantity() {
        return orderedQuantity;
    }

    public BigDecimal getFulfilledQuantity() {
        return fulfilledQuantity;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
