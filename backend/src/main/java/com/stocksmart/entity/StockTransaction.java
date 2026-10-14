package com.stocksmart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * STOCK_TRANSACTIONS — append-only stock movement history (Day 3).
 *
 * <p>One row per movement. Corrections are recorded as NEW movements, never
 * as updates or deletes of existing rows (no update/delete API exists).
 * Transfers write a paired {@code TRANSFER_OUT} + {@code TRANSFER_IN} row
 * sharing one {@code referenceNumber}.
 */
@Entity
@Table(name = "stock_transactions")
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "stock_transactions_seq")
    @SequenceGenerator(
            name = "stock_transactions_seq",
            sequenceName = "stock_transactions_seq",
            allocationSize = 1)
    @Column(name = "transaction_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_txn_product")
    )
    private Product product;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "warehouse_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_txn_warehouse")
    )
    private Warehouse warehouse;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType type;

    /** Positive quantity moved (direction is carried by {@link #type}). */
    @NotNull
    @Positive(message = "Movement quantity must be positive")
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @NotNull
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "quantity_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityBefore;

    @NotNull
    @Digits(integer = 15, fraction = 4, message = "Quantity must have at most 4 decimal places")
    @Column(name = "quantity_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityAfter;

    @NotBlank
    @Size(max = 60)
    @Column(name = "reference_number", nullable = false, length = 60)
    private String referenceNumber;

    @Size(max = 500, message = "Reason must be at most 500 characters")
    @Column(name = "reason", length = 500)
    private String reason;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "performed_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_stock_txn_user")
    )
    private User performedBy;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockTransaction() {
    }

    public StockTransaction(Product product, Warehouse warehouse, TransactionType type,
                            BigDecimal quantity, BigDecimal quantityBefore,
                            BigDecimal quantityAfter, String referenceNumber,
                            String reason, User performedBy, String notes) {
        this.product = product;
        this.warehouse = warehouse;
        this.type = type;
        this.quantity = quantity;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.referenceNumber = referenceNumber;
        this.reason = reason;
        this.performedBy = performedBy;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getReason() {
        return reason;
    }

    public User getPerformedBy() {
        return performedBy;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
