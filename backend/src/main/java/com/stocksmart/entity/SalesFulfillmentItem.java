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
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

/** One issued line within a {@link SalesFulfillment}, tied to its SO line. */
@Entity
@Table(name = "sales_fulfillment_items")
public class SalesFulfillmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_fulfillment_items_seq")
    @SequenceGenerator(
            name = "sales_fulfillment_items_seq",
            sequenceName = "sales_fulfillment_items_seq",
            allocationSize = 1)
    @Column(name = "fulfillment_item_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "fulfillment_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sof_item_fulfillment")
    )
    private SalesFulfillment fulfillment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "so_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sof_item_line")
    )
    private SalesOrderItem salesOrderItem;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sof_item_product")
    )
    private Product product;

    @NotNull
    @Positive(message = "Fulfilled quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SalesFulfillmentItem() {
    }

    public SalesFulfillmentItem(SalesOrderItem salesOrderItem, Product product,
                                BigDecimal quantity) {
        this.salesOrderItem = salesOrderItem;
        this.product = product;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SalesFulfillment getFulfillment() {
        return fulfillment;
    }

    public void setFulfillment(SalesFulfillment fulfillment) {
        this.fulfillment = fulfillment;
    }

    public SalesOrderItem getSalesOrderItem() {
        return salesOrderItem;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
