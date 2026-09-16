package com.storehub.service;

import com.storehub.entity.Permission;
import com.storehub.entity.Role;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for "which {@link Permission}s does this {@link Role} have"
 * (StoreHub Employee/User Roles spec sections 11-13, 40-41). A fixed, code-defined map —
 * not a database table, matching {@link Role} itself already being a fixed enum in this
 * codebase — so there is exactly one place authorization logic is ever duplicated from,
 * per section 40's "one centralized authorization service" instruction. Every
 * {@code @PreAuthorize("hasAuthority('PERM_...')")} check and every
 * {@code UserPrincipal.getAuthorities()} call reads from here.
 *
 * <p>ADMIN holds every permission. STORE_MANAGER holds the same operational permissions
 * ADMIN does (matching its pre-Step-5 blanket {@code hasAnyRole('ADMIN','STORE_MANAGER')}
 * access exactly, so this introduces no regression) minus the security-administration tier
 * (USER_*, ROLE_*, PERMISSION_VIEW) and FY_MANAGE (financial year close requires the
 * highest authorization per spec section 16). ACCOUNTANT/SALES_USER/PURCHASE_USER/
 * INVENTORY_USER are scoped to their named module per spec sections 17-20, deliberately
 * excluding the CANCEL-tier action spec section 23 uses as its own worked example of
 * view-without-cancel granularity.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> BY_ROLE = build();

    private RolePermissions() {
    }

    public static Set<Permission> forRole(Role role) {
        return BY_ROLE.getOrDefault(role, Collections.emptySet());
    }

    public static boolean has(Role role, Permission permission) {
        return forRole(role).contains(permission);
    }

    private static Map<Role, Set<Permission>> build() {
        Map<Role, Set<Permission>> map = new EnumMap<>(Role.class);

        Set<Permission> admin = EnumSet.allOf(Permission.class);
        map.put(Role.ADMIN, admin);

        Set<Permission> storeManager = EnumSet.copyOf(admin);
        storeManager.removeAll(EnumSet.of(
                Permission.USER_VIEW, Permission.USER_CREATE, Permission.USER_EDIT, Permission.USER_DEACTIVATE,
                Permission.ROLE_VIEW, Permission.ROLE_MANAGE, Permission.PERMISSION_VIEW,
                Permission.FY_MANAGE, Permission.AUDIT_VIEW,
                // GST config and chart-of-accounts/manual-journal are system-level (spec section 16);
                // pre-existing controllers already restrict these to ADMIN only, so STORE_MANAGER never had them.
                Permission.GST_CONFIG));
        map.put(Role.STORE_MANAGER, storeManager);

        map.put(Role.ACCOUNTANT, EnumSet.of(
                Permission.DASHBOARD_VIEW,
                Permission.PARTY_VIEW, Permission.ITEM_VIEW, Permission.MASTER_VIEW, Permission.EMPLOYEE_VIEW,
                Permission.SALES_VIEW, Permission.RECEIPT_VIEW, Permission.CREDIT_NOTE_VIEW,
                Permission.PURCHASE_VIEW, Permission.PAYMENT_VIEW, Permission.DEBIT_NOTE_VIEW,
                Permission.ACCOUNT_VIEW, Permission.JOURNAL_VIEW, Permission.JOURNAL_CREATE, Permission.JOURNAL_POST,
                Permission.EXPENSE_VIEW, Permission.EXPENSE_CREATE, Permission.EXPENSE_POST, Permission.EXPENSE_CANCEL,
                Permission.REPORT_VIEW, Permission.REPORT_EXPORT, Permission.CASH_MANAGE,
                Permission.GST_VIEW, Permission.GST_REPORT, Permission.GST_EXPORT, Permission.GST_CONFIG,
                Permission.INVENTORY_VIEW
        ));

        map.put(Role.SALES_USER, EnumSet.of(
                Permission.DASHBOARD_VIEW,
                Permission.SALES_VIEW, Permission.SALES_CREATE, Permission.SALES_EDIT, Permission.SALES_POST,
                Permission.RECEIPT_VIEW, Permission.RECEIPT_CREATE, Permission.RECEIPT_POST,
                Permission.CREDIT_NOTE_VIEW,
                Permission.POS_ACCESS,
                Permission.PARTY_VIEW, Permission.ITEM_VIEW, Permission.MASTER_VIEW
        ));

        map.put(Role.PURCHASE_USER, EnumSet.of(
                Permission.DASHBOARD_VIEW,
                Permission.PURCHASE_VIEW, Permission.PURCHASE_CREATE, Permission.PURCHASE_EDIT, Permission.PURCHASE_POST,
                Permission.PAYMENT_VIEW, Permission.PAYMENT_CREATE, Permission.PAYMENT_POST,
                Permission.DEBIT_NOTE_VIEW,
                Permission.PARTY_VIEW, Permission.ITEM_VIEW, Permission.MASTER_VIEW
        ));

        map.put(Role.INVENTORY_USER, EnumSet.of(
                Permission.DASHBOARD_VIEW,
                Permission.ITEM_VIEW, Permission.INVENTORY_VIEW, Permission.INVENTORY_ADJUST,
                Permission.MASTER_VIEW
        ));

        map.put(Role.STAFF, EnumSet.of(
                Permission.DASHBOARD_VIEW, Permission.POS_ACCESS,
                // SALES_CREATE/RECEIPT_CREATE: pre-Step-5, POST /api/sales and /api/receipts were
                // completely unguarded, so STAFF (via POS) could already reach them — kept explicit
                // here rather than left open, so the same access is now backend-enforced, not implicit.
                Permission.SALES_VIEW, Permission.SALES_CREATE,
                Permission.RECEIPT_VIEW, Permission.RECEIPT_CREATE,
                Permission.ITEM_VIEW, Permission.PARTY_VIEW, Permission.MASTER_VIEW
        ));

        return Collections.unmodifiableMap(map);
    }
}
