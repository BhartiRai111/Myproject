package com.storehub.service;

import com.storehub.entity.Role;

import java.util.EnumMap;
import java.util.Map;

/** Human-readable descriptions for the Roles &amp; Permissions admin page (spec section 60) — display only, not authorization. */
public final class RoleDescriptions {

    private static final Map<Role, String> DESCRIPTIONS = build();

    private RoleDescriptions() {
    }

    public static String of(Role role) {
        return DESCRIPTIONS.getOrDefault(role, "");
    }

    private static Map<Role, String> build() {
        Map<Role, String> map = new EnumMap<>(Role.class);
        map.put(Role.ADMIN, "Full system access, including user, role and permission management.");
        map.put(Role.STORE_MANAGER, "Broad operational access across Sales, Purchase, Inventory and reporting.");
        map.put(Role.STAFF, "Basic point-of-sale and sales access.");
        map.put(Role.ACCOUNTANT, "Accounts, expenses, GST reports and financial reporting.");
        map.put(Role.SALES_USER, "Sales, POS and receipts.");
        map.put(Role.PURCHASE_USER, "Purchases, payments and supplier-side records.");
        map.put(Role.INVENTORY_USER, "Item and stock visibility with authorized stock adjustment.");
        return map;
    }
}
