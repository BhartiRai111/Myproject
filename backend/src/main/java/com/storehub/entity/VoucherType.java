package com.storehub.entity;

/** Extensible source-transaction type for a JournalHeader. New voucher types can be added freely. */
public enum VoucherType {
    SALE,
    PURCHASE,
    RECEIPT,
    PAYMENT,
    JOURNAL,
    CREDIT_NOTE,
    DEBIT_NOTE,
    EXPENSE,
    CASH_TRANSACTION
}
