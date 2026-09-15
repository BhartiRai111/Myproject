package com.storehub.entity;

/**
 * Credit/Debit Note lifecycle (Phase 5 spec section 9). DRAFT has zero
 * side-effects (no stock, no ledger, no accounting, no GST reporting) —
 * exactly like a DRAFT Sale/Purchase. POSTED triggers all applicable
 * integrations. CANCELLED is reached only from POSTED, via a reversal
 * (never a physical delete of a posted note).
 */
public enum NoteStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
