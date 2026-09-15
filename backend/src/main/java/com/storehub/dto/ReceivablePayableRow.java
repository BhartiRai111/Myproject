package com.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One party's row in the Receivable/Payable report. {@code creditNoteAmount}/
 * {@code debitNoteAmount} are always zero: no Credit Note / Debit Note
 * transaction type exists anywhere in this app (confirmed absent in Phase 3's
 * inspection too) — the columns are kept for the report layout the spec asks
 * for, structurally present but never populated, same documented limitation
 * as GSTR-1's Credit/Debit Note sections.
 */
@Getter
@Builder
@AllArgsConstructor
public class ReceivablePayableRow {
    private Long partyId;
    private String partyName;
    private BigDecimal openingBalance;
    private BigDecimal transactionAmount;
    private BigDecimal paymentAmount;
    private BigDecimal creditNoteAmount;
    private BigDecimal debitNoteAmount;
    private BigDecimal closingOutstanding;
}
