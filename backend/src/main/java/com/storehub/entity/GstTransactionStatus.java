package com.storehub.entity;

/**
 * Lifecycle of a GstTransaction reporting row. Mirrors the JournalHeader
 * convention: a cancelled source transaction never deletes its reporting
 * row, it flips to REVERSED so the record stays auditable while GST
 * reports (which only read ACTIVE rows) correctly stop treating it as a
 * live taxable supply.
 */
public enum GstTransactionStatus {
    ACTIVE,
    REVERSED
}
