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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PRODUCTS — sellable-item master data (Day 2, extended Day 5).
 *
 * <p>Child side of CATEGORIES 1—N PRODUCTS. Carries commercial attributes
 * only — stock quantities and movements belong to Day 3 and are
 * deliberately absent here.
 *
 * <p>Day 5 additions: optional {@code barcode} (unique when present; blank
 * means "not scannable by barcode") and {@code minimumStockLevel}, the
 * product-level reorder threshold used by the dashboard low-stock rule.
 * The column is nullable for rows created before Day 5; new rows default
 * to ZERO (= no threshold configured).
 *
 * <p>Oracle mapping: NUMBER(19) PK via sequence, VARCHAR2 text,
 * NUMBER(19,2) monetary values, NUMBER(19,4) threshold.
 */
@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_products_sku", columnNames = "sku"),
                @UniqueConstraint(name = "uk_products_barcode", columnNames = "barcode")
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq")
    @SequenceGenerator(name = "products_seq", sequenceName = "products_seq", allocationSize = 1)
    @Column(name = "product_id")
    private Long id;

    /** Stock-keeping identifier — unique business key. */
    @NotBlank(message = "SKU is required")
    @Size(min = 2, max = 50, message = "SKU must be 2-50 characters")
    @Pattern(regexp = "^[A-Za-z0-9-_]+$", message = "SKU may contain only letters, digits, dash and underscore")
    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    /**
     * Optional scannable identifier (EAN/UPC/Code128 etc.), stored UPPERCASE,
     * unique when present. Lookup is case-insensitive.
     */
    @Size(max = 60, message = "Barcode must be at most 60 characters")
    @Pattern(regexp = "^[A-Za-z0-9.\\-+/]{4,60}$",
            message = "Barcode may contain only letters, digits, dot, dash, plus and slash (4-60 characters)")
    @Column(name = "barcode", length = 60)
    private String barcode;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 150, message = "Product name must be 2-150 characters")
    @Column(name = "product_name", nullable = false, length = 150)
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    @Column(name = "description", length = 1000)
    private String description;

    @NotNull(message = "Category is required")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_category")
    )
    private Category category;

    @Size(max = 100, message = "Brand must be at most 100 characters")
    @Column(name = "brand", length = 100)
    private String brand;

    @NotBlank(message = "Unit of measurement is required")
    @Size(max = 20, message = "Unit must be at most 20 characters")
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @NotNull(message = "Cost price is required")
    @DecimalMin(value = "0.00", message = "Cost price must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Cost price must have at most 2 decimal places")
    @Column(name = "cost_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal costPrice;

    @NotNull(message = "Selling price is required")
    @DecimalMin(value = "0.00", message = "Selling price must not be negative")
    @Digits(integer = 15, fraction = 2, message = "Selling price must have at most 2 decimal places")
    @Column(name = "selling_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellingPrice;

    /**
     * Product-level minimum stock level (Day 5). Low stock is evaluated per
     * product-warehouse row: {@code 0 < quantity_on_hand < minimumStockLevel}.
     * Nullable only for rows created before Day 5 (treated as ZERO).
     */
    @DecimalMin(value = "0.00", message = "Minimum stock level must not be negative")
    @Digits(integer = 15, fraction = 4, message = "Minimum stock level must have at most 4 decimal places")
    @Column(name = "minimum_stock_level", precision = 19, scale = 4)
    private BigDecimal minimumStockLevel = BigDecimal.ZERO;

    @NotNull
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String sku, String barcode, String name, String description, Category category,
                   String brand, String unit, BigDecimal costPrice,
                   BigDecimal sellingPrice, BigDecimal minimumStockLevel, boolean active) {
        this.sku = sku;
        this.barcode = barcode;
        this.name = name;
        this.description = description;
        this.category = category;
        this.brand = brand;
        this.unit = unit;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.minimumStockLevel = minimumStockLevel != null ? minimumStockLevel : BigDecimal.ZERO;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getMinimumStockLevel() {
        return minimumStockLevel;
    }

    public void setMinimumStockLevel(BigDecimal minimumStockLevel) {
        this.minimumStockLevel = minimumStockLevel;
    }

    /** Null-safe threshold: legacy rows without a value behave as "no threshold". */
    public BigDecimal getEffectiveMinimumStockLevel() {
        return minimumStockLevel != null ? minimumStockLevel : BigDecimal.ZERO;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
