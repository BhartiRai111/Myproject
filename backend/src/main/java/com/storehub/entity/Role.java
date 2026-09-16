package com.storehub.entity;

/**
 * A role is a named group of {@link Permission}s (see {@code RolePermissions} for the
 * fixed, code-defined mapping) — kept as this same enum backing {@code User.role} and the
 * JWT/{@code ROLE_*} authority every {@code @PreAuthorize} check already used before Step 5,
 * so no existing authentication/authorization wiring was replaced. ACCOUNTANT/SALES_USER/
 * PURCHASE_USER/INVENTORY_USER are additive — every pre-existing ADMIN/STORE_MANAGER/STAFF
 * check keeps working unchanged.
 */
public enum Role {
    ADMIN,
    STORE_MANAGER,
    STAFF,
    ACCOUNTANT,
    SALES_USER,
    PURCHASE_USER,
    INVENTORY_USER
}
