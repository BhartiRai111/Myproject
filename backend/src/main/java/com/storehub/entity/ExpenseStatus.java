package com.storehub.entity;

/** Expense lifecycle (Phase 6 spec section 10): DRAFT has zero accounting effect; POSTED posts the journal; CANCELLED reverses it. */
public enum ExpenseStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
