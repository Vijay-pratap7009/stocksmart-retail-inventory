package com.stocksmart.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PURCHASE_RECEIPTS — durable record of one goods-receipt operation (Day 4).
 *
 * <p>Each receipt carries a unique reference (client-supplied for idempotency
 * or generated) so a retried submission cannot receive stock twice. The linked
 * {@code STOCK_TRANSACTIONS} rows carry the same reference for traceability.
 */
@Entity
@Table(
        name = "purchase_receipts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_por_number", columnNames = "receipt_number")
        }
)
public class PurchaseReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchase_receipts_seq")
    @SequenceGenerator(
            name = "purchase_receipts_seq",
            sequenceName = "purchase_receipts_seq",
            allocationSize = 1)
    @Column(name = "receipt_id")
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "receipt_number", nullable = false, unique = true, length = 60)
    private String receiptNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "po_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_por_po")
    )
    private PurchaseOrder purchaseOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "received_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_por_user")
    )
    private User receivedBy;

    @NotNull
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReceiptItem> lines = new ArrayList<>();

    protected PurchaseReceipt() {
    }

    public PurchaseReceipt(String receiptNumber, PurchaseOrder purchaseOrder,
                           User receivedBy, String notes) {
        this.receiptNumber = receiptNumber;
        this.purchaseOrder = purchaseOrder;
        this.receivedBy = receivedBy;
        this.notes = notes;
        this.receivedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
    }

    public User getReceivedBy() {
        return receivedBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<PurchaseReceiptItem> getLines() {
        return lines;
    }

    public void addLine(PurchaseReceiptItem line) {
        line.setReceipt(this);
        this.lines.add(line);
    }
}
