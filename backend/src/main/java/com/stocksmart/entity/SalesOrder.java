package com.stocksmart.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SALES_ORDERS — sales header (Day 4).
 *
 * <p>Lifecycle: DRAFT → CONFIRMED → PARTIALLY_FULFILLED → FULFILLED, with
 * CANCELLED from any open state. Customer data stays minimal (name + contact);
 * no CRM. Confirming does NOT reserve stock — availability is enforced at
 * fulfillment time through the Day 3 inventory service.
 */
@Entity
@Table(
        name = "sales_orders",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_so_number", columnNames = "so_number")
        }
)
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sales_orders_seq")
    @SequenceGenerator(name = "sales_orders_seq", sequenceName = "sales_orders_seq", allocationSize = 1)
    @Column(name = "so_id")
    private Long id;

    @NotBlank(message = "SO number is required")
    @Size(max = 40, message = "SO number must be at most 40 characters")
    @Column(name = "so_number", nullable = false, unique = true, length = 40)
    private String soNumber;

    @NotNull(message = "Warehouse is required")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "warehouse_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_so_warehouse")
    )
    private Warehouse warehouse;

    @NotBlank(message = "Customer name is required")
    @Size(min = 2, max = 150, message = "Customer name must be 2-150 characters")
    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Size(max = 120, message = "Customer contact must be at most 120 characters")
    @Column(name = "customer_contact", length = 120)
    private String customerContact;

    @NotNull(message = "Order date is required")
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SalesOrderStatus status = SalesOrderStatus.DRAFT;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Column(name = "notes", length = 1000)
    private String notes;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_so_created_by")
    )
    private User createdBy;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SalesOrder() {
    }

    public SalesOrder(String soNumber, Warehouse warehouse, String customerName,
                      String customerContact, LocalDate orderDate, String notes, User createdBy) {
        this.soNumber = soNumber;
        this.warehouse = warehouse;
        this.customerName = customerName;
        this.customerContact = customerContact;
        this.orderDate = orderDate;
        this.notes = notes;
        this.createdBy = createdBy;
        this.status = SalesOrderStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSoNumber() {
        return soNumber;
    }

    public void setSoNumber(String soNumber) {
        this.soNumber = soNumber;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerContact() {
        return customerContact;
    }

    public void setCustomerContact(String customerContact) {
        this.customerContact = customerContact;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public void setStatus(SalesOrderStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public List<SalesOrderItem> getItems() {
        return items;
    }

    public void setItems(List<SalesOrderItem> items) {
        this.items.clear();
        if (items != null) {
            for (SalesOrderItem item : items) {
                addItem(item);
            }
        }
    }

    public void addItem(SalesOrderItem item) {
        item.setSalesOrder(this);
        this.items.add(item);
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
