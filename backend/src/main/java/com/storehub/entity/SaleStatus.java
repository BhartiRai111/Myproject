package com.storehub.entity;

public enum SaleStatus {
    /** Saved but not posted: no stock, ledger, GST-log, or accounting effects yet. Only reachable via a Kacchi Sale / Sale Challan. */
    DRAFT,
    PENDING,
    COMPLETED,
    CANCELLED
}
