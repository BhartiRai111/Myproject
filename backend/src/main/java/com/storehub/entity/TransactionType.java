package com.storehub.entity;

/**
 * Classifies a Sale/Purchase row by document kind. A "_CHALLAN" variant is a
 * Kacchi transaction: GST is still calculated exactly like the normal
 * counterpart (see {@code gstReportingApplicable} on Sale/Purchase), it is
 * simply excluded from GST return reporting (GSTR-1/GSTR-3B, Phase 3).
 */
public enum TransactionType {
    SALE,
    SALE_CHALLAN,
    PURCHASE,
    PURCHASE_CHALLAN
}
