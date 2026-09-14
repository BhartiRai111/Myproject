package com.storehub.entity;

public enum PurchaseStatus {
    /** Saved but not posted: no stock, ledger, GST-log, or accounting effects yet. Only reachable via a Kacchi Purchase / Purchase Challan. */
    DRAFT,
    PENDING,
    COMPLETED,
    CANCELLED
}
