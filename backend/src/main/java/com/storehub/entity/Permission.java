package com.storehub.entity;

/**
 * An individual authorization (StoreHub Employee/User Roles spec section 13). Fixed,
 * code-defined set — not a database table — mirroring how {@link Role} is already a
 * fixed enum in this codebase rather than a DB entity; see {@code RolePermissions} for
 * the one place that maps each {@link Role} to its permissions. Each constant carries
 * its module (spec sections 41, 62) so the Roles &amp; Permissions admin page can group
 * without a second, separately-maintained mapping.
 */
public enum Permission {

    // ---- Dashboard ----
    DASHBOARD_VIEW("Dashboard"),

    // ---- Master ----
    PARTY_VIEW("Master"), PARTY_CREATE("Master"), PARTY_EDIT("Master"),
    ITEM_VIEW("Master"), ITEM_CREATE("Master"), ITEM_EDIT("Master"),
    HSN_VIEW("Master"), HSN_EDIT("Master"),
    EMPLOYEE_VIEW("Master"), EMPLOYEE_CREATE("Master"), EMPLOYEE_EDIT("Master"),
    /** Simple lookup masters not individually named by the spec (Currency, Country, State, City, Zone, Nationality, Unit, Item Group, Payment Method, Business GST Config, Expense Category, Supplier, Customer). */
    MASTER_VIEW("Master"), MASTER_MANAGE("Master"),

    // ---- Sales ----
    SALES_VIEW("Sales"), SALES_CREATE("Sales"), SALES_EDIT("Sales"), SALES_POST("Sales"), SALES_CANCEL("Sales"),
    RECEIPT_VIEW("Sales"), RECEIPT_CREATE("Sales"), RECEIPT_POST("Sales"),
    CREDIT_NOTE_VIEW("Sales"), CREDIT_NOTE_CREATE("Sales"), CREDIT_NOTE_POST("Sales"), CREDIT_NOTE_CANCEL("Sales"),
    POS_ACCESS("Sales"),

    // ---- Purchase ----
    PURCHASE_VIEW("Purchase"), PURCHASE_CREATE("Purchase"), PURCHASE_EDIT("Purchase"), PURCHASE_POST("Purchase"), PURCHASE_CANCEL("Purchase"),
    PAYMENT_VIEW("Purchase"), PAYMENT_CREATE("Purchase"), PAYMENT_POST("Purchase"),
    DEBIT_NOTE_VIEW("Purchase"), DEBIT_NOTE_CREATE("Purchase"), DEBIT_NOTE_POST("Purchase"), DEBIT_NOTE_CANCEL("Purchase"),

    // ---- Account ----
    ACCOUNT_VIEW("Account"),
    JOURNAL_VIEW("Account"), JOURNAL_CREATE("Account"), JOURNAL_POST("Account"),
    EXPENSE_VIEW("Account"), EXPENSE_CREATE("Account"), EXPENSE_POST("Account"), EXPENSE_CANCEL("Account"),
    REPORT_VIEW("Account"), REPORT_EXPORT("Account"),
    /** Cash In/Out, Day Closing. */
    CASH_MANAGE("Account"),
    /** Financial year open/close — deliberately its own permission, never bundled with ACCOUNT_VIEW. */
    FY_MANAGE("Account"),

    // ---- GST ----
    GST_VIEW("GST"), GST_REPORT("GST"), GST_EXPORT("GST"), GST_CONFIG("GST"),

    // ---- Inventory ----
    INVENTORY_VIEW("Inventory"), INVENTORY_ADJUST("Inventory"),

    // ---- Security ----
    USER_VIEW("Security"), USER_CREATE("Security"), USER_EDIT("Security"), USER_DEACTIVATE("Security"),
    ROLE_VIEW("Security"), ROLE_MANAGE("Security"),
    PERMISSION_VIEW("Security"),

    // ---- Audit ----
    AUDIT_VIEW("Audit");

    private final String module;

    Permission(String module) {
        this.module = module;
    }

    public String getModule() {
        return module;
    }
}
