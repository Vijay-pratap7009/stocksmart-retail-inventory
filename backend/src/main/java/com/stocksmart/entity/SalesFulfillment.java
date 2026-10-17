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
import org.hibernate.annotations.BatchSize;

/**
 * SALES_FULFILLMENTS — durable record of one fulfillment operation (Day 4).
 *
 * <p>Unique fulfillment reference gives idempotency: a retried submission with
 * the same reference is rejected instead of issuing stock twice. Linked
 * {@code STOCK_TRANSACTIONS} rows (type {@code ISSUE}) carry the same
 * reference for traceability.
 */
@Entity
@Table(
        name = "sales_fulfillments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sof_number", columnNames = "fulfillment_number")
        }
)
public class SalesFulfillment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_fulfillments_seq")
    @SequenceGenerator(
            name = "sales_fulfillments_seq",
            sequenceName = "sales_fulfillments_seq",
            allocationSize = 1)
    @Column(name = "fulfillment_id")
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "fulfillment_number", nullable = false, unique = true, length = 60)
    private String fulfillmentNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "so_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sof_so")
    )
    private SalesOrder salesOrder;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "fulfilled_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_sof_user")
    )
    private User fulfilledBy;

    @NotNull
    @Column(name = "fulfilled_at", nullable = false)
    private Instant fulfilledAt;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "fulfillment", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<SalesFulfillmentItem> lines = new ArrayList<>();

    protected SalesFulfillment() {
    }

    public SalesFulfillment(String fulfillmentNumber, SalesOrder salesOrder,
                            User fulfilledBy, String notes) {
        this.fulfillmentNumber = fulfillmentNumber;
        this.salesOrder = salesOrder;
        this.fulfilledBy = fulfilledBy;
        this.notes = notes;
        this.fulfilledAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFulfillmentNumber() {
        return fulfillmentNumber;
    }

    public SalesOrder getSalesOrder() {
        return salesOrder;
    }

    public User getFulfilledBy() {
        return fulfilledBy;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public String getNotes() {
        return notes;
    }

    public List<SalesFulfillmentItem> getLines() {
        return lines;
    }

    public void addLine(SalesFulfillmentItem line) {
        line.setFulfillment(this);
        this.lines.add(line);
    }
}
