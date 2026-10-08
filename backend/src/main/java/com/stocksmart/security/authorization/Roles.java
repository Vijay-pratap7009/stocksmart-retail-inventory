package com.stocksmart.security.authorization;

/**
 * Central role names for authorization checks. Day 1 proves the backend can
 * distinguish roles; business capabilities per role arrive in later phases.
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
