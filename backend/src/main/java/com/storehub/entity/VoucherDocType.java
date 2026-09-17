package com.storehub.entity;

/**
 * Document kinds that get an FY-aware sequential voucher number (Phase 5,
 * {@code VoucherNumberService}). Deliberately separate from {@link VoucherType}
 * (the accounting engine's journal classification): a Kacchi Sale needs its
 * own "SC/26-27/000001" number series distinct from a regular Sale's
 * "SALE/26-27/000001", but both still post their accounting journal under
 * the single {@code VoucherType.SALE} bucket — numbering granularity and
 * accounting-bucket granularity are different concerns and must not be
 * conflated (see GstReportingEligibility's own similar separation of
 * concerns for tax calc vs. reporting eligibility).
 */
public enum VoucherDocType {
    SALE("SALE"),
    SALE_CHALLAN("SC"),
    PURCHASE("PUR"),
    PURCHASE_CHALLAN("PC"),
    SALES_ORDER("SO"),
    PURCHASE_ORDER("PO"),
    RECEIPT("REC"),
    PAYMENT("PAY"),
    CREDIT_NOTE("CN"),
    DEBIT_NOTE("DN"),
    EXPENSE("EXP"),
    CASH_TRANSACTION("CT"),
    STOCK_TRANSFER("ST");

    private final String prefix;

    VoucherDocType(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
