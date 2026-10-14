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

/** One received line within a {@link PurchaseReceipt}, tied to its PO line. */
@Entity
@Table(name = "purchase_receipt_items")
public class PurchaseReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchase_receipt_items_seq")
    @SequenceGenerator(
            name = "purchase_receipt_items_seq",
            sequenceName = "purchase_receipt_items_seq",
            allocationSize = 1)
    @Column(name = "receipt_item_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receipt_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_por_item_receipt")
    )
    private PurchaseReceipt receipt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "po_item_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_por_item_line")
    )
    private PurchaseOrderItem purchaseOrderItem;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_por_item_product")
    )
    private Product product;

    @NotNull
    @Positive(message = "Received quantity must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseReceiptItem() {
    }

    public PurchaseReceiptItem(PurchaseOrderItem purchaseOrderItem, Product product,
                               BigDecimal quantity) {
        this.purchaseOrderItem = purchaseOrderItem;
        this.product = product;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PurchaseReceipt getReceipt() {
        return receipt;
    }

    public void setReceipt(PurchaseReceipt receipt) {
        this.receipt = receipt;
    }

    public PurchaseOrderItem getPurchaseOrderItem() {
        return purchaseOrderItem;
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
