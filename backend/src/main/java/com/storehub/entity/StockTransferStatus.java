package com.storehub.entity;

/**
 * Store-to-store stock transfer workflow (Multi-Store spec section 20):
 * DRAFT -> APPROVED -> DISPATCHED -> RECEIVED, or CANCELLED from DRAFT/APPROVED
 * before any stock has actually moved. Stock only ever leaves the source store
 * at DISPATCHED and only ever lands in the destination store at RECEIVED — never
 * at DRAFT/APPROVED, which are purely a paperwork/authorization stage.
 */
public enum StockTransferStatus {
    DRAFT,
    APPROVED,
    DISPATCHED,
    RECEIVED,
    CANCELLED
}
