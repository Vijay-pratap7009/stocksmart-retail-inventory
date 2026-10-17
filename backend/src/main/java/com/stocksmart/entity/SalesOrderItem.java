package com.stocksmart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.BatchSize;

/**
 * SO_ITEMS — one requested product line. {@code unitPrice} is the selling price
 * captured at order time; later product price changes never rewrite it.
 */
@BatchSize(size = 50)
@Entity
@Table(name = "so_items")
public class SalesOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "so_items_seq")
    @SequenceGenerator(name = "so_items_seq", sequenceName = "so_items_seq", allocationSize = 1)
    @Column(name = "so_item_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "so_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_so_item_so")
    )
    private SalesOrder salesOrder;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_so_item_product")
    )
    private Product product;

    @NotNull(message = "Ordered quantity is required")
    @Positive(message = "Ordered quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderedQuantity;

    @NotNull
    @DecimalMin(value = "0.00", message = "Fulfilled quantity must not be negative")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "fulfilled_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal fulfilledQuantity = BigDecimal.ZERO;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.00", message = "Unit price must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Unit price must have at most 2 decimal places")
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SalesOrderItem() {
    }

    public SalesOrderItem(Product product, BigDecimal orderedQuantity, BigDecimal unitPrice) {
        this.product = product;
        this.orderedQuantity = orderedQuantity;
        this.fulfilledQuantity = BigDecimal.ZERO;
        this.unitPrice = unitPrice;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SalesOrder getSalesOrder() {
        return salesOrder;
    }

    public void setSalesOrder(SalesOrder salesOrder) {
        this.salesOrder = salesOrder;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public BigDecimal getOrderedQuantity() {
        return orderedQuantity;
    }

    public void setOrderedQuantity(BigDecimal orderedQuantity) {
        this.orderedQuantity = orderedQuantity;
    }

    public BigDecimal getFulfilledQuantity() {
        return fulfilledQuantity;
    }

    public void setFulfilledQuantity(BigDecimal fulfilledQuantity) {
        this.fulfilledQuantity = fulfilledQuantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
