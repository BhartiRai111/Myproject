package com.storehub.entity;

/**
 * Whether posting a Credit/Debit Note should move physical stock. A
 * SALES_RETURN/PURCHASE_RETURN note is STOCK_RETURN; a pure price/discount/
 * tax adjustment or a customer-credit note with no goods movement is
 * FINANCIAL_ADJUSTMENT — never touches Inventory (Phase 5 spec section 7).
 */
public enum StockImpactType {
    STOCK_RETURN,
    FINANCIAL_ADJUSTMENT
}
