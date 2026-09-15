package com.storehub.entity;

/** Ad-hoc cash/bank movement not already covered by Receipt (customer money in) or Payment (supplier money out) — spec section 12. */
public enum CashTransactionType {
    CASH_IN,
    CASH_OUT
}
