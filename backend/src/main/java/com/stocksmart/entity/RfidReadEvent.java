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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * RFID_READ_EVENTS — durable log of processed tag reads (Day 5).
 *
 * <p>Each processed scan records one row: the raw EPC (even for unknown
 * tags), the resolved tag/product when available, the reader source, an
 * event timestamp and whether the read came from the simulator. The unique
 * {@code eventReference} is the idempotency key: retrying the same read
 * request is rejected instead of logging/duplicating work.
 *
 * <p>Reads are informational — this table records no stock effects.
 */
@Entity
@Table(
        name = "rfid_read_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rfid_read_reference", columnNames = "event_reference")
        }
)
public class RfidReadEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rfid_read_events_seq")
    @SequenceGenerator(
            name = "rfid_read_events_seq",
            sequenceName = "rfid_read_events_seq",
            allocationSize = 1)
    @Column(name = "read_event_id")
    private Long id;

    @NotBlank
    @Size(max = 60)
    @Column(name = "event_reference", nullable = false, unique = true, length = 60)
    private String eventReference;

    /** Raw scanned EPC as reported by the reader (kept even when unknown). */
    @NotBlank
    @Size(max = 96)
    @Column(name = "epc_code", nullable = false, length = 96)
    private String epcCode;

    /** Resolved tag, null when the EPC is unknown. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tag_id", foreignKey = @ForeignKey(name = "fk_rfid_read_tag"))
    private RfidTag tag;

    /** Product resolved through the tag mapping, null for unknown tags. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_rfid_read_product"))
    private Product product;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RfidReadStatus status;

    @Size(max = 60)
    @Column(name = "reader_name", length = 60)
    private String readerName;

    /** True when produced by the built-in simulator (never claimed as hardware). */
    @NotNull
    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    /** Reader/event timestamp. */
    @NotNull
    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    /** Server-side receipt timestamp. */
    @NotNull
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "performed_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_rfid_read_user")
    )
    private User performedBy;

    protected RfidReadEvent() {
    }

    public RfidReadEvent(String eventReference, String epcCode, RfidTag tag, Product product,
                         RfidReadStatus status, String readerName, boolean simulated,
                         Instant eventAt, User performedBy) {
        this.eventReference = eventReference;
        this.epcCode = epcCode;
        this.tag = tag;
        this.product = product;
        this.status = status;
        this.readerName = readerName;
        this.simulated = simulated;
        this.eventAt = eventAt;
        this.receivedAt = Instant.now();
        this.performedBy = performedBy;
    }

    public Long getId() {
        return id;
    }

    public String getEventReference() {
        return eventReference;
    }

    public String getEpcCode() {
        return epcCode;
    }

    public RfidTag getTag() {
        return tag;
    }

    public Product getProduct() {
        return product;
    }

    public RfidReadStatus getStatus() {
        return status;
    }

    public String getReaderName() {
        return readerName;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Instant getEventAt() {
        return eventAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public User getPerformedBy() {
        return performedBy;
    }
}
