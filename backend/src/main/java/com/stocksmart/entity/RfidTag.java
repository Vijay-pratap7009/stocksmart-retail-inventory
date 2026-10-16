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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * RFID_TAGS — EPC-to-product mapping (Day 5).
 *
 * <p>One row per physical RFID tag. The EPC code is unique; a tag maps to
 * exactly one product and can be deactivated without losing history. The
 * table intentionally stores NO inventory quantities — balances live only
 * in {@link Inventory} (Day 3) and a tag read never changes stock by itself.
 */
@Entity
@Table(
        name = "rfid_tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rfid_tags_epc", columnNames = "epc_code")
        }
)
public class RfidTag {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rfid_tags_seq")
    @SequenceGenerator(name = "rfid_tags_seq", sequenceName = "rfid_tags_seq", allocationSize = 1)
    @Column(name = "tag_id")
    private Long id;

    /** RFID tag identifier (EPC or vendor tag id), stored UPPERCASE; unique. */
    @NotBlank(message = "EPC code is required")
    @Size(min = 4, max = 96, message = "EPC code must be 4-96 characters")
    @Pattern(regexp = "^[A-Za-z0-9.\\-:]{4,96}$",
            message = "EPC code may contain only letters, digits, dot, dash and colon")
    @Column(name = "epc_code", nullable = false, unique = true, length = 96)
    private String epcCode;

    @NotNull(message = "Product is required")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_rfid_tag_product")
    )
    private Product product;

    @NotNull
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "registered_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_rfid_tag_user")
    )
    private User registeredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected RfidTag() {
    }

    public RfidTag(String epcCode, Product product, boolean active, User registeredBy) {
        this.epcCode = epcCode;
        this.product = product;
        this.active = active;
        this.registeredBy = registeredBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEpcCode() {
        return epcCode;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public User getRegisteredBy() {
        return registeredBy;
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
