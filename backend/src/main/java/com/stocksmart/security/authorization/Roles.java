package com.stocksmart.security.authorization;

/**
 * Central role names for authorization checks.
 *
 * <p>Day 2 baseline for master data (enforced via {@code @PreAuthorize} on
 * the category / product / warehouse / supplier controllers):
 *
 * <ul>
 *   <li>ADMIN — full master-data access (all modules, read + write).</li>
 *   <li>INVENTORY_MANAGER — product, category and warehouse management
 *       (read + write); read access to suppliers.</li>
 *   <li>PROCUREMENT_USER — supplier management (read + write); read access
 *       to categories, products and warehouses.</li>
 *   <li>WAREHOUSE_STAFF — read access to categories, products, warehouses.</li>
 *   <li>SALES_USER — read access to categories and products.</li>
 *   <li>MANAGEMENT — read-only master-data access.</li>
 * </ul>
 *
 * <p>Day 3 baseline for inventory (enforced via {@code @PreAuthorize} on
 * the inventory controller plus URL matchers in {@code SecurityConfig}):
 *
 * <ul>
 *   <li>ADMIN — full inventory access, receipts, adjustments, transfers.</li>
 *   <li>INVENTORY_MANAGER — view inventory/history, receive, adjust, transfer.</li>
 *   <li>WAREHOUSE_STAFF — view inventory/history, receive, adjust, transfer
 *       (authorized warehouse operations).</li>
 *   <li>PROCUREMENT_USER — read inventory/history for procurement decisions.</li>
 *   <li>SALES_USER — read inventory availability.</li>
 *   <li>MANAGEMENT — read-only inventory and transaction history.</li>
 * </ul>
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String INVENTORY_MANAGER = "INVENTORY_MANAGER";
    public static final String WAREHOUSE_STAFF = "WAREHOUSE_STAFF";
    public static final String PROCUREMENT_USER = "PROCUREMENT_USER";
    public static final String SALES_USER = "SALES_USER";
    public static final String MANAGEMENT = "MANAGEMENT";

    private Roles() {
    }

    /** Spring Security authority string for a role, e.g. {@code ROLE_ADMIN}. */
    public static String authority(String role) {
        return "ROLE_" + role;
    }
}
