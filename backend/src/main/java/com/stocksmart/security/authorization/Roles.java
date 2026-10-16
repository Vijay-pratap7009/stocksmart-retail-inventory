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
 *
 * <p>Day 4 baseline for orders (enforced via {@code @PreAuthorize} on
 * the order controllers plus URL matchers in {@code SecurityConfig}):
 *
 * <ul>
 *   <li>ADMIN — full order access (purchase + sales, receipts, fulfillments).</li>
 *   <li>PROCUREMENT_USER — purchase order reads+writes and goods receipts;
 *       no sales order access.</li>
 *   <li>SALES_USER — sales order reads+writes and fulfillments;
 *       no purchase order access.</li>
 *   <li>INVENTORY_MANAGER — order reads; authorized receipts and fulfillments.</li>
 *   <li>WAREHOUSE_STAFF — order reads; authorized receiving/fulfillment operations.</li>
 *   <li>MANAGEMENT — read-only order information.</li>
 * </ul>
 *
 * <p>Day 5 baseline for barcode/RFID identification and KPI visibility
 * (enforced via {@code @PreAuthorize} on the RFID/dashboard controllers plus
 * URL matchers in {@code SecurityConfig}):
 *
 * <ul>
 *   <li>ADMIN — manage RFID tag mappings; use scanning workflows; full dashboard.</li>
 *   <li>INVENTORY_MANAGER — use scanning workflows; read RFID data; inventory
 *       KPIs + both order summaries; stock changes only via authorized paths.</li>
 *   <li>WAREHOUSE_STAFF — product identification (barcode/RFID reads);
 *       inventory KPIs + both order summaries.</li>
 *   <li>PROCUREMENT_USER — product identification; inventory KPIs and the
 *       purchase-order summary (no sales-fulfillment summary).</li>
 *   <li>SALES_USER — product identification; inventory KPIs and the
 *       sales-fulfillment summary (no purchase-order summary).</li>
 *   <li>MANAGEMENT — read-only: barcode/RFID lookups and KPIs; cannot
 *       process RFID reads or write tag mappings.</li>
 * </ul>
 * Barcode/SKU lookup itself is read-only and open to every authenticated
 * role; it never changes stock.
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
