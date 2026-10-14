package com.stocksmart.dto;

import java.math.BigDecimal;

/** Read model for one purchase order line, including receipt progress. */
public class PurchaseOrderLineResponse {

    private Long id;
    private Long productId;
    private String sku;
    private String productName;
    private BigDecimal orderedQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal unitCost;
    private BigDecimal lineTotal;

    public PurchaseOrderLineResponse() {
    }

    public PurchaseOrderLineResponse(Long id, Long productId, String sku, String productName,
                                     BigDecimal orderedQuantity, BigDecimal receivedQuantity,
                                     BigDecimal unitCost) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.productName = productName;
        this.orderedQuantity = orderedQuantity;
        this.receivedQuantity = receivedQuantity;
        this.remainingQuantity = orderedQuantity.subtract(receivedQuantity);
        this.unitCost = unitCost;
        this.lineTotal = orderedQuantity.multiply(unitCost);
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

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
